package com.xiaomo305mua.morphoptifinecompat;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class MOFCTransformer implements IClassTransformer {

    private static final String EVENT_HANDLER = "morph.common.core.EventHandler";
    private static final String EVENT_HANDLER_INTERNAL = EVENT_HANDLER.replace('.', '/');
    private static final String MODEL_HELPER = "morph.client.model.ModelHelper";
    private static final String MODEL_HELPER_INTERNAL = MODEL_HELPER.replace('.', '/');
    private static final String RENDER_HAND_METHOD = "onRenderHand";
    private static final String GL_ALLOCATION = "net/minecraft/client/renderer/GLAllocation";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String DELETE_DISPLAY_LIST_SRG = "func_74523_b";
    private static final String DELETE_DISPLAY_LIST_MCP = "deleteDisplayLists";
    private static final String HELPER = "deleteDisplayListSafe";
    private static final String GUARD = "renderingMorphHand";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        try {
            String n = name.replace('/', '.');
            String t = transformedName == null ? "" : transformedName.replace('/', '.');
            if (n.equals(EVENT_HANDLER) || t.equals(EVENT_HANDLER)) {
                return patchEventHandler(basicClass);
            }
            if (n.equals(MODEL_HELPER) || t.equals(MODEL_HELPER)) {
                return patchModelHelper(basicClass);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private byte[] patchModelHelper(byte[] bytes) {
        ClassNode cn = read(bytes);

        boolean hasHelper = false;
        boolean redirected = false;

        for (MethodNode m : cn.methods) {
            if (HELPER.equals(m.name) && "(I)V".equals(m.desc)) {
                hasHelper = true;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (GL_ALLOCATION.equals(call.owner)
                            && (DELETE_DISPLAY_LIST_SRG.equals(call.name) || DELETE_DISPLAY_LIST_MCP.equals(call.name))
                            && "(I)V".equals(call.desc)) {
                        call.owner = MODEL_HELPER_INTERNAL;
                        call.name = HELPER;
                        call.desc = "(I)V";
                        redirected = true;
                    }
                }
            }
        }

        if (!redirected) {
            return bytes;
        }
        if (!hasHelper) {
            cn.methods.add(buildDeleteDisplayListSafe());
        }
        return write(cn);
    }

    private static MethodNode buildDeleteDisplayListSafe() {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, HELPER, "(I)V", null, null);
        InsnList ins = m.instructions;
        LabelNode ret = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();

        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new JumpInsnNode(Opcodes.IFLE, ret));
        ins.add(start);
        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_ALLOCATION, DELETE_DISPLAY_LIST_SRG, "(I)V", false));
        ins.add(end);
        ins.add(new JumpInsnNode(Opcodes.GOTO, ret));
        ins.add(handler);
        ins.add(new InsnNode(Opcodes.POP));
        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new InsnNode(Opcodes.ICONST_1));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL11, "glDeleteLists", "(II)V", false));
        ins.add(ret);
        ins.add(new InsnNode(Opcodes.RETURN));

        m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/NullPointerException"));
        return m;
    }

    private byte[] patchEventHandler(byte[] bytes) {
        ClassNode cn = read(bytes);

        boolean hasField = false;
        MethodNode target = null;

        for (FieldNode f : cn.fields) {
            if (GUARD.equals(f.name) && "Z".equals(f.desc)) {
                hasField = true;
            }
        }
        for (MethodNode m : cn.methods) {
            if (RENDER_HAND_METHOD.equals(m.name)) {
                target = m;
            }
        }

        if (target == null) {
            return bytes;
        }
        if (!hasField) {
            cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, GUARD, "Z", null, null));
        }
        injectGuard(target);
        return write(cn);
    }

    private static void injectGuard(MethodNode m) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode skip = new LabelNode();

        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new FieldInsnNode(Opcodes.GETFIELD, EVENT_HANDLER_INTERNAL, GUARD, "Z"));
        head.add(new JumpInsnNode(Opcodes.IFNE, skip));
        setFlag(head, true);
        head.add(start);
        m.instructions.insert(head);

        boolean endSet = false;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                InsnList reset = new InsnList();
                if (!endSet) {
                    reset.add(end);
                    endSet = true;
                }
                setFlag(reset, false);
                m.instructions.insertBefore(insn, reset);
            }
        }

        InsnList tail = new InsnList();
        tail.add(handler);
        setFlag(tail, false);
        tail.add(new InsnNode(Opcodes.ATHROW));
        tail.add(skip);
        tail.add(new InsnNode(Opcodes.RETURN));
        m.instructions.add(tail);

        m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
    }

    private static void setFlag(InsnList list, boolean value) {
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new InsnNode(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        list.add(new FieldInsnNode(Opcodes.PUTFIELD, EVENT_HANDLER_INTERNAL, GUARD, "Z"));
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    private static byte[] write(ClassNode cn) {
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
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