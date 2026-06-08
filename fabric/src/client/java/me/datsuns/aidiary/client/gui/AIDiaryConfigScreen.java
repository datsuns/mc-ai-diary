package me.datsuns.aidiary.client.gui;

import me.datsuns.aidiary.AIDiaryClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.OrderedText;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public class AIDiaryConfigScreen extends Screen {
    private final Screen parent;
    private final String initialApiKey;
    private final Consumer<String> saveCallback;

    private TextFieldWidget apiKeyBox;
    private ButtonWidget saveButton;
    private ButtonWidget validateButton;
    private TextWidget validationStatusWidget;

    public AIDiaryConfigScreen(Screen parent, String initialApiKey, Consumer<String> saveCallback) {
        super(Text.translatable("aidiary.config.title"));
        this.parent = parent;
        this.initialApiKey = initialApiKey != null ? initialApiKey : "";
        this.saveCallback = saveCallback;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        TextWidget titleWidget = new TextWidget(centerX - 150, 20, 300, 20, this.title, this.textRenderer);
        this.addDrawableChild(titleWidget);

        TextWidget labelWidget = new TextWidget(centerX - 150, centerY - 35, 300, 10, Text.translatable("aidiary.config.apikey.label"), this.textRenderer);
        this.addDrawableChild(labelWidget);

        this.apiKeyBox = new TextFieldWidget(this.textRenderer, centerX - 150, centerY - 20, 200, 20, Text.translatable("aidiary.config.apikey.placeholder"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setText(this.initialApiKey);

        try {
            boolean found = false;
            for (java.lang.reflect.Method m : TextFieldWidget.class.getMethods()) {
                if (m.getName().equals("setRenderTextProvider") || m.getName().equals("setFormatter") || m.getName().equals("method_73210") || m.getName().equals("addFormatter")) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInterface()) {
                        System.out.println("FOUND METHOD: " + m.getName() + " taking " + m.getParameterTypes()[0].getName());
                        m.invoke(this.apiKeyBox, java.lang.reflect.Proxy.newProxyInstance(
                            TextFieldWidget.class.getClassLoader(),
                            new Class[]{m.getParameterTypes()[0]},
                            (proxy, method, args) -> {
                                if (method.getName().equals("format") || method.getName().equals("apply") || method.getName().equals("applyAsCharSequence") || method.getName().equals("provide") || method.getName().equals("method_73211")) {
                                    String str = (String) args[0];
                                    String masked = "*".repeat(str.length());
                                    return OrderedText.styledForwardsVisitedString(masked, Style.EMPTY);
                                }
                                return null;
                            }
                        ));
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                System.out.println("COULD NOT FIND FORMATTER METHOD. AVAILABLE METHODS:");
                for (java.lang.reflect.Method m : TextFieldWidget.class.getMethods()) {
                    System.out.println(" - " + m.getName() + " taking " + (m.getParameterCount() > 0 ? m.getParameterTypes()[0].getName() : "none"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.addDrawableChild(this.apiKeyBox);

        this.validateButton = ButtonWidget.builder(Text.translatable("aidiary.config.button.validate"), button -> validateApiKey())
                .dimensions(centerX + 60, centerY - 20, 90, 20)
                .build();
        this.addDrawableChild(this.validateButton);

        this.validationStatusWidget = new TextWidget(centerX - 150, centerY + 5, 300, 20, Text.empty(), this.textRenderer);
        this.addDrawableChild(this.validationStatusWidget);

        this.saveButton = ButtonWidget.builder(Text.translatable("aidiary.config.button.save"), button -> saveAndClose())
                .dimensions(centerX - 150, centerY + 40, 145, 20)
                .build();
        this.addDrawableChild(this.saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("aidiary.config.button.cancel"), button -> this.client.setScreen(this.parent))
                .dimensions(centerX + 5, centerY + 40, 145, 20)
                .build();
        this.addDrawableChild(cancelButton);
    }

    private void saveAndClose() {
        this.saveCallback.accept(this.apiKeyBox.getText());
        this.client.setScreen(this.parent);
    }

    private void validateApiKey() {
        String key = this.apiKeyBox.getText();
        if (key.isEmpty()) {
            this.validationStatusWidget.setMessage(Text.translatable("aidiary.config.status.empty").setStyle(Style.EMPTY.withColor(0xFF5555)));
            return;
        }

        this.validationStatusWidget.setMessage(Text.translatable("aidiary.config.status.validating").setStyle(Style.EMPTY.withColor(0xFFFF55)));
        this.validateButton.active = false;

        Thread validationThread = new Thread(() -> {
            try {
                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + key;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                this.client.execute(() -> {
                    this.validateButton.active = true;
                    if (response.statusCode() == 200) {
                        this.validationStatusWidget.setMessage(Text.translatable("aidiary.config.status.success").setStyle(Style.EMPTY.withColor(0x55FF55)));
                    } else {
                        this.validationStatusWidget.setMessage(Text.translatable("aidiary.config.status.failed", response.statusCode()).setStyle(Style.EMPTY.withColor(0xFF5555)));
                    }
                });

            } catch (Exception e) {
                this.client.execute(() -> {
                    this.validateButton.active = true;
                    this.validationStatusWidget.setMessage(Text.translatable("aidiary.config.status.error", e.getMessage()).setStyle(Style.EMPTY.withColor(0xFF5555)));
                });
            }
        });
        validationThread.start();
    }
}
