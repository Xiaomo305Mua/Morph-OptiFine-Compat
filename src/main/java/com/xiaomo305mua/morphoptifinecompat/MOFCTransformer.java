package com.xiaomo305mua.morphoptifinecompat;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

public class MOFCTransformer implements IClassTransformer {

    private static final String EVENT_HANDLER = "morph.common.core.EventHandler";
    private static final String EVENT_HANDLER_INTERNAL = "morph/common/core/EventHandler";
    private static final String MODEL_HELPER = "morph.client.model.ModelHelper";
    private static final String MODEL_HELPER_INTERNAL = "morph/client/model/ModelHelper";
    private static final String RENDER_HAND_EVENT = "net/minecraftforge/client/event/RenderHandEvent";
    private static final String GL_ALLOCATION = "net/minecraft/client/renderer/GLAllocation";
    private static final String GL11 = "org/lwjgl/opengl/GL11";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String n = name.replace('/', '.');
        String tn = transformedName == null ? "" : transformedName.replace('/', '.');
        if (n.equals(EVENT_HANDLER) || tn.equals(EVENT_HANDLER)) {
            return patchEventHandler(basicClass);
        }
        if (n.equals(MODEL_HELPER) || tn.equals(MODEL_HELPER)) {
            return patchModelHelper(basicClass);
        }
        return basicClass;
    }

    private byte[] patchModelHelper(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);

        int redirected = 0;
        boolean hasHelper = false;
        for (MethodNode method : classNode.methods) {
            if ("deleteDisplayListSafe".equals(method.name) && "(I)V".equals(method.desc)) {
                hasHelper = true;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    if (GL_ALLOCATION.equals(m.owner)
                            && ("func_74523_b".equals(m.name) || "deleteDisplayLists".equals(m.name))
                            && "(I)V".equals(m.desc)) {
                        m.owner = MODEL_HELPER_INTERNAL;
                        m.name = "deleteDisplayListSafe";
                        m.desc = "(I)V";
                        redirected++;
                    }
                }
            }
        }

        boolean patched = redirected > 0;
        if (!hasHelper) {
            classNode.methods.add(buildDeleteDisplayListSafe());
            patched = true;
        }
        if (!patched) {
            return bytes;
        }

        ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode buildDeleteDisplayListSafe() {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "deleteDisplayListSafe", "(I)V", null, null);
        InsnList ins = method.instructions;
        LabelNode ret = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();

        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new JumpInsnNode(Opcodes.IFLE, ret));
        ins.add(start);
        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_ALLOCATION, "func_74523_b", "(I)V", false));
        ins.add(end);
        ins.add(new JumpInsnNode(Opcodes.GOTO, ret));
        ins.add(handler);
        ins.add(new InsnNode(Opcodes.POP));
        ins.add(new VarInsnNode(Opcodes.ILOAD, 0));
        ins.add(new InsnNode(Opcodes.ICONST_1));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL11, "glDeleteLists", "(II)V", false));
        ins.add(ret);
        ins.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/NullPointerException"));
        return method;
    }

    private byte[] patchEventHandler(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);

        boolean hasField = false;
        MethodNode target = null;
        for (FieldNode field : classNode.fields) {
            if ("renderingMorphHand".equals(field.name) && "Z".equals(field.desc)) {
                hasField = true;
            }
        }
        for (MethodNode method : classNode.methods) {
            if ("onRenderHand".equals(method.name) && "(" + RENDER_HAND_EVENT + ")V".equals(method.desc)) {
                target = method;
            }
        }

        boolean patched = false;
        if (!hasField) {
            classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "renderingMorphHand", "Z", null, null));
            patched = true;
        }
        if (target != null) {
            InsnList head = new InsnList();
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            LabelNode skip = new LabelNode();

            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new FieldInsnNode(Opcodes.GETFIELD, EVENT_HANDLER_INTERNAL, "renderingMorphHand", "Z"));
            head.add(new JumpInsnNode(Opcodes.IFNE, skip));
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new InsnNode(Opcodes.ICONST_1));
            head.add(new FieldInsnNode(Opcodes.PUTFIELD, EVENT_HANDLER_INTERNAL, "renderingMorphHand", "Z"));
            head.add(start);
            target.instructions.insert(head);

            boolean endEmitted = false;
            for (AbstractInsnNode insn : target.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList reset = new InsnList();
                    if (!endEmitted) {
                        reset.add(end);
                        endEmitted = true;
                    }
                    reset.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    reset.add(new InsnNode(Opcodes.ICONST_0));
                    reset.add(new FieldInsnNode(Opcodes.PUTFIELD, EVENT_HANDLER_INTERNAL, "renderingMorphHand", "Z"));
                    target.instructions.insertBefore(insn, reset);
                }
            }

            InsnList tail = new InsnList();
            tail.add(handler);
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new InsnNode(Opcodes.ICONST_0));
            tail.add(new FieldInsnNode(Opcodes.PUTFIELD, EVENT_HANDLER_INTERNAL, "renderingMorphHand", "Z"));
            tail.add(new InsnNode(Opcodes.ATHROW));
            tail.add(skip);
            tail.add(new InsnNode(Opcodes.RETURN));
            target.instructions.add(tail);

            target.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
            patched = true;
        }
        if (!patched) {
            return bytes;
        }

        ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
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