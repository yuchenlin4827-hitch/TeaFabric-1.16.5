package teafabric.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Generates {@code teafabric.generated.GenericSupers.get(Class)}: the generic superclass of every
 * Gson {@code TypeToken} subclass ({@code new TypeToken<Map<String, Foo>>() {}}), built from the
 * class's Signature attribute at build time because TeaVM has no run-time generic reflection.
 * Types are constructed with Gson's own {@code $Gson$Types} factories.
 */
public final class GenericSupersGenerator implements Opcodes {
    public static final String CLASS_NAME = "teafabric/generated/GenericSupers";
    private static final String GSON_TYPES = "com/google/gson/internal/$Gson$Types";
    private static final String TYPE = "java/lang/reflect/Type";
    private static final String[] TOKEN_CLASSES = {"com/google/gson/reflect/TypeToken"};

    private final Map<String, byte[]> classes;

    public GenericSupersGenerator(Map<String, byte[]> classes) {
        this.classes = classes;
    }

    public byte[] generate() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "get", "(Ljava/lang/Class;)L" + TYPE + ";",
                null, null);
        mv.visitCode();
        int count = 0;
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            ClassReader reader = new ClassReader(e.getValue());
            String superName = reader.getSuperName();
            boolean token = false;
            for (String t : TOKEN_CLASSES) {
                token |= t.equals(superName);
            }
            if (!token) {
                continue;
            }
            String[] signature = new String[1];
            reader.accept(new org.objectweb.asm.ClassVisitor(ASM9) {
                @Override
                public void visit(int version, int access, String name, String sig, String sup, String[] itf) {
                    signature[0] = sig;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (signature[0] == null) {
                continue;
            }
            Label next = new Label();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(Type.getObjectType(e.getKey()));
            mv.visitJumpInsn(IF_ACMPNE, next);
            emitSuperclass(mv, signature[0]);
            mv.visitInsn(ARETURN);
            mv.visitLabel(next);
            count++;
        }
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        System.out.println("[tables] Generated " + CLASS_NAME + " for " + count + " TypeToken subclasses");
        return cw.toByteArray();
    }

    /** Pushes a java.lang.reflect.Type for the superclass part of a class signature. */
    private void emitSuperclass(MethodVisitor mv, String classSignature) {
        TypeNode root = new TypeNode();
        new SignatureReader(classSignature).accept(new SignatureVisitor(ASM9) {
            @Override
            public SignatureVisitor visitSuperclass() {
                return new Builder(root);
            }
        });
        emit(mv, root);
    }

    /** Small type tree built from the signature, then turned into bytecode. */
    static final class TypeNode {
        String className; // internal name, for class / parameterized types
        char primitive;   // descriptor char for primitives
        TypeNode component; // arrays
        char wildcard;    // '+', '-', '*' when this node is a wildcard argument
        TypeNode bound;
        final List<TypeNode> args = new ArrayList<>();
        TypeNode owner;   // for inner classes
        boolean typeVariable;
    }

    static final class Builder extends SignatureVisitor {
        private TypeNode node;

        Builder(TypeNode node) {
            super(ASM9);
            this.node = node;
        }

        @Override
        public void visitBaseType(char descriptor) {
            node.primitive = descriptor;
        }

        @Override
        public void visitTypeVariable(String name) {
            node.typeVariable = true;
        }

        @Override
        public SignatureVisitor visitArrayType() {
            node.component = new TypeNode();
            return new Builder(node.component);
        }

        @Override
        public void visitClassType(String name) {
            node.className = name;
        }

        @Override
        public void visitInnerClassType(String name) {
            TypeNode outer = new TypeNode();
            outer.className = node.className;
            outer.args.addAll(node.args);
            outer.owner = node.owner;
            node.owner = outer;
            node.args.clear();
            node.className = node.className + "$" + name;
        }

        @Override
        public void visitTypeArgument() {
            TypeNode w = new TypeNode();
            w.wildcard = '*';
            node.args.add(w);
        }

        @Override
        public SignatureVisitor visitTypeArgument(char kind) {
            TypeNode arg = new TypeNode();
            if (kind == SignatureVisitor.INSTANCEOF) {
                node.args.add(arg);
                return new Builder(arg);
            }
            TypeNode w = new TypeNode();
            w.wildcard = kind;
            w.bound = arg;
            node.args.add(w);
            return new Builder(arg);
        }
    }

    private void emit(MethodVisitor mv, TypeNode n) {
        if (n.wildcard != 0) {
            if (n.wildcard == '*') {
                mv.visitLdcInsn(Type.getType(Object.class));
                mv.visitMethodInsn(INVOKESTATIC, GSON_TYPES, "subtypeOf",
                        "(L" + TYPE + ";)Ljava/lang/reflect/WildcardType;", false);
            } else {
                emit(mv, n.bound);
                mv.visitMethodInsn(INVOKESTATIC, GSON_TYPES, n.wildcard == '+' ? "subtypeOf" : "supertypeOf",
                        "(L" + TYPE + ";)Ljava/lang/reflect/WildcardType;", false);
            }
            return;
        }
        if (n.typeVariable) {
            mv.visitLdcInsn(Type.getType(Object.class)); // erased: no reflection on type variables
            return;
        }
        if (n.primitive != 0) {
            mv.visitLdcInsn(Type.getType(String.valueOf(n.primitive)));
            return;
        }
        if (n.component != null) {
            emit(mv, n.component);
            mv.visitMethodInsn(INVOKESTATIC, GSON_TYPES, "arrayOf",
                    "(L" + TYPE + ";)Ljava/lang/reflect/GenericArrayType;", false);
            return;
        }
        if (n.args.isEmpty() && n.owner == null) {
            mv.visitLdcInsn(Type.getObjectType(n.className));
            return;
        }
        // $Gson$Types.newParameterizedTypeWithOwner(owner, rawType, typeArguments...)
        if (n.owner != null) {
            emit(mv, n.owner);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitLdcInsn(Type.getObjectType(n.className));
        mv.visitLdcInsn(n.args.size());
        mv.visitTypeInsn(ANEWARRAY, TYPE);
        for (int i = 0; i < n.args.size(); i++) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(i);
            emit(mv, n.args.get(i));
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESTATIC, GSON_TYPES, "newParameterizedTypeWithOwner",
                "(L" + TYPE + ";L" + TYPE + ";[L" + TYPE + ";)Ljava/lang/reflect/ParameterizedType;", false);
    }
}
