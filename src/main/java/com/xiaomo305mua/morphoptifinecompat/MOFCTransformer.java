package com.xiaomo305mua.morphoptifinecompat;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MOFCTransformer implements IClassTransformer {

    private static final String EVENT_HANDLER = "morph/common/core/EventHandler";
    private static final String MODEL_HELPER = "morph/client/model/ModelHelper";
    private static final String RENDER_HAND_EVENT = "net/minecraftforge/client/event/RenderHandEvent";
    private static final String GL_ALLOCATION = "net/minecraft/client/renderer/GLAllocation";
    private static final String GL11 = "org/lwjgl/opengl/GL11";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (name.equals(EVENT_HANDLER) || transformedName.equals(EVENT_HANDLER)) {
            return patchEventHandler(basicClass);
        }
        if (name.equals(MODEL_HELPER) || transformedName.equals(MODEL_HELPER)) {
            return patchModelHelper(basicClass);
        }
        return basicClass;
    }

    private byte[] patchEventHandler(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM4, cw) {

            private boolean hasField;

            @Override
            public FieldVisitor visitField(int access, String fname, String fdesc, String signature, Object value) {
                if (fname.equals("renderingMorphHand") && fdesc.equals("Z")) {
                    hasField = true;
                }
                return super.visitField(access, fname, fdesc, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String mname, String mdesc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
                if (mname.equals("onRenderHand") && mdesc.equals("(" + RENDER_HAND_EVENT + ")V")) {
                    return new GuardMethodVisitor(mv);
                }
                return mv;
            }

            @Override
            public void visitEnd() {
                if (!hasField) {
                    super.visitField(Opcodes.ACC_PRIVATE, "renderingMorphHand", "Z", null, null);
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    private byte[] patchModelHelper(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM4, cw) {

            private boolean hasHelper;

            @Override
            public MethodVisitor visitMethod(int access, String mname, String mdesc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, mname, mdesc, signature, exceptions);
                if (mname.equals("deleteDisplayListSafe") && mdesc.equals("(I)V")) {
                    hasHelper = true;
                }
                return new MethodVisitor(Opcodes.ASM4, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && owner.equals(GL_ALLOCATION)
                                && (mname.equals("func_74523_b") || mname.equals("deleteDisplayLists"))
                                && mdesc.equals("(I)V")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, MODEL_HELPER, "deleteDisplayListSafe", "(I)V", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                        }
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!hasHelper) {
                    addDeleteDisplayListSafe(super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "deleteDisplayListSafe", "(I)V", null, null));
                }
                super.visitEnd();
            }
        }, 0);
        return cw.toByteArray();
    }

    private void addDeleteDisplayListSafe(MethodVisitor mv) {
        Label ret = new Label();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();

        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitJumpInsn(Opcodes.IFLE, ret);
        mv.visitTryCatchBlock(start, end, handler, "java/lang/NullPointerException");
        mv.visitLabel(start);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, GL_ALLOCATION, "func_74523_b", "(I)V", false);
        mv.visitLabel(end);
        mv.visitJumpInsn(Opcodes.GOTO, ret);
        mv.visitLabel(handler);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, GL11, "glDeleteLists", "(II)V", false);
        mv.visitLabel(ret);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static class GuardMethodVisitor extends MethodVisitor {

        private final Label start = new Label();
        private final Label end = new Label();
        private final Label handler = new Label();
        private final Label skip = new Label();
        private boolean endEmitted;

        GuardMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM4, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, EVENT_HANDLER, "renderingMorphHand", "Z");
            mv.visitJumpInsn(Opcodes.IFNE, skip);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, EVENT_HANDLER, "renderingMorphHand", "Z");
            mv.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
            mv.visitLabel(start);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTFIELD, EVENT_HANDLER, "renderingMorphHand", "Z");
                if (!endEmitted) {
                    mv.visitLabel(end);
                    endEmitted = true;
                }
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitLabel(handler);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitFieldInsn(Opcodes.PUTFIELD, EVENT_HANDLER, "renderingMorphHand", "Z");
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitLabel(skip);
            mv.visitInsn(Opcodes.RETURN);
            super.visitMaxs(maxStack, maxLocals);
        }
    }

    private static class SafeClassWriter extends ClassWriter {
        SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}