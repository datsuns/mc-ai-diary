package me.datsuns.aidiary.client.gui;

import me.datsuns.aidiary.AIDiaryClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

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

    private EditBox apiKeyBox;
    private Button saveButton;
    private Button validateButton;
    private StringWidget validationStatusWidget;
    private String validationStatus = "";

    public AIDiaryConfigScreen(Screen parent, String initialApiKey, Consumer<String> saveCallback) {
        super(Component.translatable("aidiary.config.title"));
        this.parent = parent;
        this.initialApiKey = initialApiKey != null ? initialApiKey : "";
        this.saveCallback = saveCallback;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        StringWidget titleWidget = new StringWidget(centerX - 150, 20, 300, 20, this.title, this.font);
        this.addRenderableWidget(titleWidget);

        StringWidget labelWidget = new StringWidget(centerX - 150, centerY - 35, 300, 10, Component.translatable("aidiary.config.apikey.label"), this.font);
        this.addRenderableWidget(labelWidget);

        this.apiKeyBox = new EditBox(this.font, centerX - 150, centerY - 20, 200, 20, Component.translatable("aidiary.config.apikey.placeholder"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(this.initialApiKey);
        
        try {
            for (java.lang.reflect.Method m : EditBox.class.getMethods()) {
                if (m.getName().equals("addFormatter")) {
                    m.invoke(this.apiKeyBox, java.lang.reflect.Proxy.newProxyInstance(
                        EditBox.class.getClassLoader(),
                        new Class[]{m.getParameterTypes()[0]},
                        (proxy, method, args) -> {
                            if (method.getName().equals("format") || method.getName().equals("apply") || method.getName().equals("applyAsCharSequence")) {
                                String str = (String) args[0];
                                String masked = "*".repeat(str.length());
                                return FormattedCharSequence.forward(masked, Style.EMPTY);
                            }
                            return null;
                        }
                    ));
                    break;
                }
            }
        } catch (Exception e) {}
        
        this.addRenderableWidget(this.apiKeyBox);

        this.validateButton = Button.builder(Component.translatable("aidiary.config.button.validate"), button -> validateApiKey())
                .pos(centerX + 60, centerY - 20)
                .size(90, 20)
                .build();
        this.addRenderableWidget(this.validateButton);

        this.validationStatusWidget = new StringWidget(centerX - 150, centerY + 5, 300, 20, Component.empty(), this.font);
        this.addRenderableWidget(this.validationStatusWidget);

        this.saveButton = Button.builder(Component.translatable("aidiary.config.button.save"), button -> saveAndClose())
                .pos(centerX - 150, centerY + 40)
                .size(145, 20)
                .build();
        this.addRenderableWidget(this.saveButton);

        Button cancelButton = Button.builder(Component.translatable("aidiary.config.button.cancel"), button -> this.minecraft.gui.setScreen(this.parent))
                .pos(centerX + 5, centerY + 40)
                .size(145, 20)
                .build();
        this.addRenderableWidget(cancelButton);
    }

    private void saveAndClose() {
        this.saveCallback.accept(this.apiKeyBox.getValue());
        this.minecraft.gui.setScreen(this.parent);
    }

    private void validateApiKey() {
        String key = this.apiKeyBox.getValue();
        if (key.isEmpty()) {
            this.validationStatusWidget.setMessage(Component.translatable("aidiary.config.status.empty").withStyle(Style.EMPTY.withColor(0xFF5555)));
            return;
        }

        this.validationStatusWidget.setMessage(Component.translatable("aidiary.config.status.validating").withStyle(Style.EMPTY.withColor(0xFFFF55)));
        this.validateButton.active = false;

        Thread validationThread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + key;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                this.minecraft.execute(() -> {
                    this.validateButton.active = true;
                    if (response.statusCode() == 200) {
                        this.validationStatusWidget.setMessage(Component.translatable("aidiary.config.status.success").withStyle(Style.EMPTY.withColor(0x55FF55)));
                    } else {
                        this.validationStatusWidget.setMessage(Component.translatable("aidiary.config.status.failed", response.statusCode()).withStyle(Style.EMPTY.withColor(0xFF5555)));
                    }
                });

            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    this.validateButton.active = true;
                    this.validationStatusWidget.setMessage(Component.translatable("aidiary.config.status.error", e.getMessage()).withStyle(Style.EMPTY.withColor(0xFF5555)));
                });
            }
        });
        validationThread.start();
    }
}
