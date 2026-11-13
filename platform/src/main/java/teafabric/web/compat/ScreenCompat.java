package teafabric.web.compat;

import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_310;
import net.minecraft.class_339;
import net.minecraft.class_364;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_442;

/**
 * Screen adjustments for the browser. The title screen loses its Realms button (Realms needs a Microsoft
 * account and Mojang's servers, neither of which the page can use) and the Realms notifications, which
 * would try to reach those servers in the background; the buttons below move up into the gap.
 */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /** Screen.init(MinecraftClient, int, int) (redirect target). */
    public static void method_25423(class_437 screen, class_310 client, int width, int height) {
        boolean title = screen instanceof class_442;
        if (title) {
            client.field_1690.field_1830 = false; // realmsNotifications
        }
        screen.method_25423(client, width, height);
        if (title) {
            hideRealmsButton(screen);
        }
    }

    private static void hideRealmsButton(class_437 screen) {
        class_339 realms = null;
        for (class_364 element : screen.method_25396()) {
            if (element instanceof class_4185) {
                class_2561 message = ((class_4185) element).method_25369();
                if (message instanceof class_2588 && "menu.online".equals(((class_2588) message).method_11022())) {
                    realms = (class_339) element;
                    break;
                }
            }
        }
        if (realms == null) {
            return;
        }
        realms.field_22764 = false; // visible
        realms.field_22763 = false; // active
        int y = realms.field_22761;
        for (class_364 element : screen.method_25396()) {
            if (element instanceof class_339 && element != realms && ((class_339) element).field_22761 > y) {
                ((class_339) element).field_22761 -= 24;
            }
        }
    }
}
