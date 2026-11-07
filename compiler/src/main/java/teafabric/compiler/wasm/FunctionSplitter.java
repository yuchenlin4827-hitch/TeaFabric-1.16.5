package teafabric.compiler.wasm;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.instruction.*;

/**
 * Cuts huge generated straight-line functions into many small ones.
 *
 * <p>TeaVM emits the module start function ({@code teavm@initializer}: class metadata, vtables, static
 * field defaults for every class) and the reflection table builder ({@code teavm@initReflection}) as
 * single functions — about 4 MB and 3 MB of code for Minecraft. Executing them once exhausts V8's
 * dynamic-tiering budget, so V8 queues them for its optimising compiler on a background thread, which
 * then runs out of zone memory ("V8 process OOM (Zone)") and kills the renderer. Each piece produced here
 * is small, runs once and is never worth optimising.
 *
 * <p>Only functions of type {@code () -> void} without locals are split, and only at top-level points
 * where the operand stack is empty, so every piece is a sequence of complete statements. Functions that
 * return early, branch to the function body or contain anything whose stack effect is not known are
 * left untouched.
 */
public final class FunctionSplitter {
    private static final int MIN_SIZE = 40_000;
    private static final int CHUNK_SIZE = 4_000;

    private FunctionSplitter() {
    }

    public static void split(WasmModule module) {
        List<WasmFunction> candidates = new ArrayList<>();
        for (WasmFunction function : module.functions) {
            if (function.getImportName() == null
                    && function.getType().getParameterTypes().isEmpty()
                    && function.getType().getReturnTypes().isEmpty()
                    && function.getLocalVariables().isEmpty()
                    && size(function.getBody()) >= MIN_SIZE) {
                candidates.add(function);
            }
        }
        for (WasmFunction function : candidates) {
            int parts = split(module, function);
            if (parts > 0) {
                System.out.println("[teavm] split " + function.getName() + " into " + parts + " functions");
            } else {
                System.out.println("[teavm] could not split " + function.getName());
            }
        }
    }

    private static int split(WasmModule module, WasmFunction function) {
        WasmInstructionList body = function.getBody();
        if (!splittable(body, body)) {
            return 0;
        }
        // Plan cut points first (instruction after which the stack is empty and the chunk is big enough).
        StackEffect effect = new StackEffect();
        List<WasmInstruction> cuts = new ArrayList<>();
        int height = 0;
        int chunk = 0;
        for (WasmInstruction insn = body.getFirst(); insn != null; insn = insn.getNext()) {
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
            chunk += size(insn);
            if (height == 0 && chunk >= CHUNK_SIZE) {
                cuts.add(insn);
                chunk = 0;
            }
        }
        if (height != 0 || cuts.size() < 2) {
            return 0;
        }
        WasmInstructionList original = new WasmInstructionList();
        original.transferFrom(body);
        int index = 0;
        int cut = 0;
        WasmFunction part = null;
        for (WasmInstruction insn = original.getFirst(); insn != null; ) {
            WasmInstruction next = insn.getNext();
            if (part == null) {
                part = new WasmFunction(function.getType());
                part.setName(function.getName() + "@part" + index++);
                module.functions.add(part);
                body.add(new WasmCall(part));
            }
            insn.delete();
            part.getBody().add(insn);
            if (cut < cuts.size() && insn == cuts.get(cut)) {
                cut++;
                part = null;
            }
            insn = next;
        }
        return index;
    }

    /** Rejects bodies with early returns or branches whose target is the function body itself. */
    private static boolean splittable(WasmInstructionList list, WasmInstructionList functionBody) {
        for (WasmInstruction insn = list.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof WasmReturn) {
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
