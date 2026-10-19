package net.multiskin.bodyparts;

import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public enum BodyPart {
    RIGHT_ARM(1, "command.multi-skin.bodyparts.right_arm"),
    LEFT_ARM(1 << 1, "command.multi-skin.bodyparts.left_arm"),
    RIGHT_LEG(1 << 2, "command.multi-skin.bodyparts.right_leg"),
    LEFT_LEG(1 << 3, "command.multi-skin.bodyparts.left_leg");

    private final int mask;
    private final String translationKey;

    BodyPart(int mask, String translationKey) {
        this.mask = mask;
        this.translationKey = translationKey;
    }

    public int mask() {
        return mask;
    }

    public String translationKey() {
        return translationKey;
    }

    public static BodyPart arm(Arm arm) {
        return arm == Arm.RIGHT ? RIGHT_ARM : LEFT_ARM;
    }

    public static boolean isArmRemoved(int mask, Arm arm) {
        return (mask & arm(arm).mask) != 0;
    }

    public static boolean isHandArmRemoved(int mask, Arm mainArm, Hand hand) {
        Arm arm = hand == Hand.MAIN_HAND ? mainArm : mainArm.getOpposite();
        return isArmRemoved(mask, arm);
    }

    public static boolean hasNoLegs(int mask) {
        return (mask & (RIGHT_LEG.mask | LEFT_LEG.mask)) == (RIGHT_LEG.mask | LEFT_LEG.mask);
    }

    public static boolean hasNoArms(int mask) {
        return (mask & (RIGHT_ARM.mask | LEFT_ARM.mask)) == (RIGHT_ARM.mask | LEFT_ARM.mask);
    }

    public static boolean hasOneArm(int mask) {
        int arms = mask & (RIGHT_ARM.mask | LEFT_ARM.mask);
        return arms == RIGHT_ARM.mask || arms == LEFT_ARM.mask;
    }

    public static boolean hasOneLeg(int mask) {
        int legs = mask & (RIGHT_LEG.mask | LEFT_LEG.mask);
        return legs == RIGHT_LEG.mask || legs == LEFT_LEG.mask;
    }
}
