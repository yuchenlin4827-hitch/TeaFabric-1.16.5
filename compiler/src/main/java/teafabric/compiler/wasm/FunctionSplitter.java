package teafabric.compiler.wasm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.instruction.*;

/**
 * Cuts huge generated straight-line functions into many small ones.
 *
 * <p>TeaVM emits the module start function ({@code teavm@initializer}: class metadata, vtables, static
 * field defaults for every class) and the reflection table builder ({@code teavm@initReflection}) as
 * single functions — about 4 MB and 3 MB of code for Minecraft — and huge class initialisers such as
 * {@code Blocks.<clinit>} (230 KB, tens of thousands of strict-mode null checks). When V8 tiers such a
 * function up, Turboshaft's Wasm GC type analysis runs out of zone memory on a background thread
 * ("V8 process OOM (Zone)") and the renderer dies; this was confirmed with
 * {@code --wasm-tier-up-filter=<Blocks.<clinit>>}. Small pieces compile fine.
 *
 * <p>Only functions of type {@code () -> void} are split, and only at top-level points where the operand
 * stack is empty and no local variable carries a value across (backward liveness over the top-level
 * instructions; writes inside nested blocks are conservatively not counted as definite). Each piece gets
 * its own copies of the locals it uses. Functions that return early, branch to the function body or contain
 * anything whose stack effect is not known (e.g. coroutine state machines) are left untouched.
 */
public final class FunctionSplitter {
    private static final int MIN_SIZE = 8_000;
    private static final int CHUNK_SIZE = 3_000;

    private FunctionSplitter() {
    }

    public static void split(WasmModule module) {
        List<WasmFunction> candidates = new ArrayList<>();
        for (WasmFunction function : module.functions) {
            if (function.getImportName() == null
                    && function.getType().getParameterTypes().isEmpty()
                    && function.getType().getReturnTypes().isEmpty()
                    && size(function.getBody()) >= MIN_SIZE) {
                candidates.add(function);
            }
        }
        int split = 0;
        int pieces = 0;
        for (WasmFunction function : candidates) {
            int parts = split(module, function);
            if (parts > 0) {
                split++;
                pieces += parts;
                if (parts >= 20) {
                    System.out.println("[teavm] split " + function.getName() + " into " + parts + " functions");
                }
            } else {
                System.out.println("[teavm] could not split " + function.getName());
            }
        }
        System.out.println("[teavm] split " + split + " of " + candidates.size() + " large functions into "
                + pieces + " pieces");
    }

    private static int split(WasmModule module, WasmFunction function) {
        WasmInstructionList body = function.getBody();
        if (!splittable(body, body)) {
            return 0;
        }
        // Top-level instructions, their stack heights and the locals live between them.
        List<WasmInstruction> insns = new ArrayList<>();
        for (WasmInstruction insn = body.getFirst(); insn != null; insn = insn.getNext()) {
            insns.add(insn);
        }
        int n = insns.size();
        if (n > 0 && insns.get(n - 1) instanceof WasmReturn) {
            n--; // a final return stays in the last piece, after which the original function ends anyway
        }
        StackEffect effect = new StackEffect();
        boolean[] stackEmptyAfter = new boolean[n];
        int height = 0;
        for (int i = 0; i < n; i++) {
            WasmInstruction insn = insns.get(i);
            if (insn.isTerminating()) {
                return 0;
            }
            effect.reset();
            insn.acceptVisitor(effect);
            if (effect.unknown) {
                return 0;
            }
            height += effect.push - effect.pop;
            if (height < 0) {
                return 0;
            }
            stackEmptyAfter[i] = height == 0;
        }
        if (height != 0) {
            return 0;
        }
        boolean[] noLiveAfter = new boolean[n];
        BitSet live = new BitSet();
        for (int i = n - 1; i >= 0; i--) {
            noLiveAfter[i] = live.isEmpty();
            WasmInstruction insn = insns.get(i);
            if (insn instanceof WasmSetLocal) {
                live.clear(((WasmSetLocal) insn).getLocal().getIndex());
            } else if (insn instanceof WasmTeeLocal) {
                live.clear(((WasmTeeLocal) insn).getLocal().getIndex());
            } else {
                collectReads(insn, live);
            }
        }
        List<WasmInstruction> cuts = new ArrayList<>();
        int chunk = 0;
        for (int i = 0; i < n; i++) {
            chunk += size(insns.get(i));
            if (chunk >= CHUNK_SIZE && stackEmptyAfter[i] && noLiveAfter[i]) {
                cuts.add(insns.get(i));
                chunk = 0;
            }
        }
        if (cuts.size() < 2) {
            return 0;
        }
        WasmInstructionList original = new WasmInstructionList();
        original.transferFrom(body);
        int index = 0;
        int cut = 0;
        WasmFunction part = null;
        Map<WasmLocal, WasmLocal> locals = new HashMap<>();
        for (WasmInstruction insn = original.getFirst(); insn != null; ) {
            WasmInstruction next = insn.getNext();
            if (part == null) {
                part = new WasmFunction(function.getType());
                part.setName(function.getName() + "@part" + index++);
                part.setJavaMethod(function.getJavaMethod());
                module.functions.add(part);
                body.add(new WasmCall(part));
                locals.clear();
            }
            insn.delete();
            remapLocals(insn, part, locals);
            part.getBody().add(insn);
            if (cut < cuts.size() && insn == cuts.get(cut)) {
                cut++;
                part = null;
            }
            insn = next;
        }
        return index;
    }

    /** Locals that {@code insn} (including nested code) may read. */
    private static void collectReads(WasmInstruction insn, BitSet out) {
        if (insn instanceof WasmGetLocal) {
            out.set(((WasmGetLocal) insn).getLocal().getIndex());
        } else if (insn instanceof WasmBlock) {
            collectReads(((WasmBlock) insn).getBody(), out);
        } else if (insn instanceof WasmConditional) {
            collectReads(((WasmConditional) insn).getThenBlock(), out);
            collectReads(((WasmConditional) insn).getElseBlock(), out);
        } else if (insn instanceof WasmTry) {
            collectReads(((WasmTry) insn).getBody(), out);
        }
    }

    private static void collectReads(WasmInstructionList list, BitSet out) {
        for (WasmInstruction insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            collectReads(insn, out);
        }
    }

    /** Points local accesses (including nested code) at the piece's own copies of the locals. */
    private static void remapLocals(WasmInstruction insn, WasmFunction part, Map<WasmLocal, WasmLocal> map) {
        if (insn instanceof WasmGetLocal) {
            WasmGetLocal get = (WasmGetLocal) insn;
            get.setLocal(copy(get.getLocal(), part, map));
        } else if (insn instanceof WasmSetLocal) {
            WasmSetLocal set = (WasmSetLocal) insn;
            set.setLocal(copy(set.getLocal(), part, map));
        } else if (insn instanceof WasmTeeLocal) {
            WasmTeeLocal tee = (WasmTeeLocal) insn;
            tee.setLocal(copy(tee.getLocal(), part, map));
        } else if (insn instanceof WasmBlock) {
            remapLocals(((WasmBlock) insn).getBody(), part, map);
        } else if (insn instanceof WasmConditional) {
            remapLocals(((WasmConditional) insn).getThenBlock(), part, map);
            remapLocals(((WasmConditional) insn).getElseBlock(), part, map);
        } else if (insn instanceof WasmTry) {
            remapLocals(((WasmTry) insn).getBody(), part, map);
        }
    }

    private static void remapLocals(WasmInstructionList list, WasmFunction part, Map<WasmLocal, WasmLocal> map) {
        for (WasmInstruction insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            remapLocals(insn, part, map);
        }
    }

    private static WasmLocal copy(WasmLocal local, WasmFunction part, Map<WasmLocal, WasmLocal> map) {
        return map.computeIfAbsent(local, l -> {
            WasmLocal result = new WasmLocal(l.getType(), l.getName());
            result.setJavaType(l.getJavaType());
            part.add(result);
            return result;
        });
    }

    /** Rejects bodies with early returns or branches whose target is the function body itself. */
    private static boolean splittable(WasmInstructionList list, WasmInstructionList functionBody) {
        for (WasmInstruction insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof WasmReturn && !(list == functionBody && insn.getNext() == null)) {
                return false;
            }
            if (insn instanceof WasmBreak && ((WasmBreak) insn).getTarget() == functionBody
                    || insn instanceof WasmBranch && ((WasmBranch) insn).getTarget() == functionBody
                    || insn instanceof WasmNullBranch && ((WasmNullBranch) insn).getTarget() == functionBody
                    || insn instanceof WasmCastBranch && ((WasmCastBranch) insn).getTarget() == functionBody) {
                return false;
            }
            if (insn instanceof WasmSwitch) {
                WasmSwitch sw = (WasmSwitch) insn;
                if (sw.getDefaultTarget() == functionBody || sw.getTargets().contains(functionBody)) {
                    return false;
                }
            }
            if (insn instanceof WasmBlock && !splittable(((WasmBlock) insn).getBody(), functionBody)) {
                return false;
            }
            if (insn instanceof WasmConditional) {
                WasmConditional c = (WasmConditional) insn;
                if (!splittable(c.getThenBlock(), functionBody) || !splittable(c.getElseBlock(), functionBody)) {
                    return false;
                }
            }
            if (insn instanceof WasmTry) {
                WasmTry t = (WasmTry) insn;
                for (WasmCatchClause clause : t.getCatches()) {
                    if (clause.getTarget() == functionBody) {
                        return false;
                    }
                }
                if (!splittable(t.getBody(), functionBody)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int size(WasmInstructionList list) {
        int n = 0;
        for (WasmInstruction insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            n += size(insn);
        }
        return n;
    }

    private static int size(WasmInstruction insn) {
        if (insn instanceof WasmBlock) {
            return 1 + size(((WasmBlock) insn).getBody());
        }
        if (insn instanceof WasmConditional) {
            WasmConditional c = (WasmConditional) insn;
            return 1 + size(c.getThenBlock()) + size(c.getElseBlock());
        }
        if (insn instanceof WasmTry) {
            return 1 + size(((WasmTry) insn).getBody());
        }
        return 1;
    }

    /** Net operand stack effect of one instruction (only the net value matters for finding cut points). */
    private static final class StackEffect implements WasmInstructionVisitor {
        int pop;
        int push;
        boolean unknown;

        void reset() {
            pop = 0;
            push = 0;
            unknown = false;
        }

        private void set(int pop, int push) {
            this.pop = pop;
            this.push = push;
        }

        private void block(WasmBlockType type, int extraPop) {
            if (type == null) {
                set(extraPop, 0);
            } else {
                set(type.getInputTypes().size() + extraPop, type.getOutputTypes().size());
            }
        }

        @Override public void visit(WasmUnreachable insn) { unknown = true; }
        @Override public void visit(WasmBlock insn) { block(insn.getType(), 0); }
        @Override public void visit(WasmConditional insn) { block(insn.getType(), 1); }
        @Override public void visit(WasmBranch insn) { set(1, 0); }
        @Override public void visit(WasmNullBranch insn) {
            set(1, insn.getCondition() == WasmNullCondition.NULL ? 1 : 0);
        }
        @Override public void visit(WasmCastBranch insn) { set(1, 1); }
        @Override public void visit(WasmBreak insn) { unknown = true; }
        @Override public void visit(WasmSwitch insn) { unknown = true; }
        @Override public void visit(WasmReturn insn) { unknown = true; }
        @Override public void visit(WasmInt32Constant insn) { set(0, 1); }
        @Override public void visit(WasmInt64Constant insn) { set(0, 1); }
        @Override public void visit(WasmFloat32Constant insn) { set(0, 1); }
        @Override public void visit(WasmFloat64Constant insn) { set(0, 1); }
        @Override public void visit(WasmNullConstant insn) { set(0, 1); }
        @Override public void visit(WasmIsNull insn) { set(1, 1); }
        @Override public void visit(WasmGetLocal insn) { set(0, 1); }
        @Override public void visit(WasmSetLocal insn) { set(1, 0); }
        @Override public void visit(WasmTeeLocal insn) { set(1, 1); }
        @Override public void visit(WasmGetGlobal insn) { set(0, 1); }
        @Override public void visit(WasmSetGlobal insn) { set(1, 0); }
        @Override public void visit(WasmIntBinary insn) { set(2, 1); }
        @Override public void visit(WasmFloatBinary insn) { set(2, 1); }
        @Override public void visit(WasmIntUnary insn) { set(1, 1); }
        @Override public void visit(WasmFloatUnary insn) { set(1, 1); }
        @Override public void visit(WasmConversion insn) { set(1, 1); }
        @Override public void visit(WasmCall insn) {
            set(insn.getFunction().getType().getParameterTypes().size(),
                    insn.getFunction().getType().getReturnTypes().size());
        }
        @Override public void visit(WasmIndirectCall insn) {
            set(insn.getType().getParameterTypes().size() + 1, insn.getType().getReturnTypes().size());
        }
        @Override public void visit(WasmCallReference insn) {
            set(insn.getType().getParameterTypes().size() + 1, insn.getType().getReturnTypes().size());
        }
        @Override public void visit(WasmDrop insn) { set(1, 0); }
        @Override public void visit(WasmLoadInt32 insn) { set(1, 1); }
        @Override public void visit(WasmLoadInt64 insn) { set(1, 1); }
        @Override public void visit(WasmLoadFloat32 insn) { set(1, 1); }
        @Override public void visit(WasmLoadFloat64 insn) { set(1, 1); }
        @Override public void visit(WasmStoreInt32 insn) { set(2, 0); }
        @Override public void visit(WasmStoreInt64 insn) { set(2, 0); }
        @Override public void visit(WasmStoreFloat32 insn) { set(2, 0); }
        @Override public void visit(WasmStoreFloat64 insn) { set(2, 0); }
        @Override public void visit(WasmMemoryGrow insn) { set(1, 1); }
        @Override public void visit(WasmFill insn) { set(3, 0); }
        @Override public void visit(WasmCopy insn) { set(3, 0); }
        @Override public void visit(WasmTry insn) { set(0, insn.getType() == null ? 0 : 1); }
        @Override public void visit(WasmThrow insn) { unknown = true; }
        @Override public void visit(WasmReferencesEqual insn) { set(2, 1); }
        @Override public void visit(WasmCast insn) { set(1, 1); }
        @Override public void visit(WasmTest insn) { set(1, 1); }
        @Override public void visit(WasmExternConversion insn) { set(1, 1); }
        @Override public void visit(WasmStructNew insn) { set(insn.getType().getFields().size(), 1); }
        @Override public void visit(WasmStructNewDefault insn) { set(0, 1); }
        @Override public void visit(WasmStructGet insn) { set(1, 1); }
        @Override public void visit(WasmStructSet insn) { set(2, 0); }
        @Override public void visit(WasmArrayNewDefault insn) { set(1, 1); }
        @Override public void visit(WasmArrayNewFixed insn) { set(insn.getSize(), 1); }
        @Override public void visit(WasmArrayGet insn) { set(2, 1); }
        @Override public void visit(WasmArraySet insn) { set(3, 0); }
        @Override public void visit(WasmArrayLength insn) { set(1, 1); }
        @Override public void visit(WasmArrayCopy insn) { set(5, 0); }
        @Override public void visit(WasmFunctionReference insn) { set(0, 1); }
        @Override public void visit(WasmInt31Reference insn) { set(1, 1); }
        @Override public void visit(WasmInt31Get insn) { set(1, 1); }
    }
}
