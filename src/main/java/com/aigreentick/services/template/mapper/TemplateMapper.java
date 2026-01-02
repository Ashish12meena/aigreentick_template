package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.build.SupportedAppDto;
import com.aigreentick.services.template.dto.build.TemplateCarouselButton;
import com.aigreentick.services.template.dto.build.TemplateCarouselCardComponent;
import com.aigreentick.services.template.dto.build.TemplateCarouselExample;
import com.aigreentick.services.template.dto.build.TemplateComponentButtonDto;
import com.aigreentick.services.template.dto.build.TemplateComponentCardsDto;
import com.aigreentick.services.template.dto.build.TemplateComponentDto;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.build.TemplateExampleDto;
import com.aigreentick.services.template.dto.build.TemplateTextDto;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.SupportedAppRequest;
import com.aigreentick.services.template.dto.request.TemplateCarouselButtonRequest;
import com.aigreentick.services.template.dto.request.TemplateCarouselCardComponentRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentButtonRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentCardsRequest;
import com.aigreentick.services.template.dto.request.TemplateComponentRequest;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.request.TemplateTextRequest;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.ButtonTypes;
import com.aigreentick.services.template.enums.MediaFormat;
import com.aigreentick.services.template.model.SupportedApp;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.model.TemplateCarouselCard;
import com.aigreentick.services.template.model.TemplateCarouselCardButton;
import com.aigreentick.services.template.model.TemplateComponent;
import com.aigreentick.services.template.model.TemplateComponentButton;
import com.aigreentick.services.template.model.TemplateText;

@Component
public class TemplateMapper {

    public TemplateResponseDto mapToTemplateResponse(Template template) {
        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status(template.getStatus())
                .category(template.getCategory())
                .language(template.getLanguage())
                .metaTemplateId(template.getWaId())
                .build();
    }

    /**
     * Maps a Facebook API template response to a Template entity.
     * Used during sync operations.
     */
    public Template fromFacebookTemplate(TemplateRequest fbTemplate, Long userId, String wabaId) {
        Template template = Template.builder()
                .userId(userId)
                .name(fbTemplate.getName())
                .language(fbTemplate.getLanguage())
                .category(fbTemplate.getCategory())
                .previousCategory(fbTemplate.getPreviousCategory())
                .status(fbTemplate.getStatus() != null ? fbTemplate.getStatus().getValue() : null)
                .waId(fbTemplate.getMetaTemplateId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (fbTemplate.getComponents() != null) {
            for (TemplateComponentRequest compReq : fbTemplate.getComponents()) {
                template.addComponent(toComponent(compReq));
            }
        }

        return template;
    }

    /**
     * Maps CreateTemplateResponseDto to Template entity.
     * Used during template creation flow.
     */
    public Template toTemplateEntity(CreateTemplateResponseDto request, Long userId) {
        TemplateRequest req = request.getTemplate();

        Template template = Template.builder()
                .userId(userId)
                .name(req.getName())
                .language(req.getLanguage())
                .category(req.getCategory())
                .previousCategory(req.getPreviousCategory())
                .status(req.getStatus() != null ? req.getStatus().getValue() : "PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (req.getComponents() != null) {
            for (TemplateComponentRequest compReq : req.getComponents()) {
                template.addComponent(toComponent(compReq));
            }
        }

        if (request.getVariables() != null) {
            for (TemplateTextRequest textReq : request.getVariables()) {
                template.addText(toText(textReq));
            }
        }

        return template;
    }

    /**
     * Converts Template entity to TemplateDto for building sendable templates.
     * This is the key method for reusing template building logic.
     */
    public TemplateDto toTemplateDto(Template template) {
        TemplateDto dto = new TemplateDto();
        dto.setId(template.getId().toString());
        dto.setName(template.getName());
        dto.setCategory(template.getCategory());
        dto.setLanguage(template.getLanguage());
        dto.setStatus(template.getStatus());
        dto.setMetaTemplateId(template.getWaId());

        // Map components
        if (template.getComponents() != null && !template.getComponents().isEmpty()) {
            dto.setComponents(template.getComponents().stream()
                    .map(this::toComponentDto)
                    .collect(Collectors.toList()));
        }

        // Map texts/variables
        if (template.getTexts() != null && !template.getTexts().isEmpty()) {
            dto.setTexts(template.getTexts().stream()
                    .map(this::toTextDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private TemplateComponentDto toComponentDto(TemplateComponent component) {
        TemplateComponentDto.TemplateComponentDtoBuilder builder = TemplateComponentDto.builder()
                .type(component.getType())
                .format(component.getFormat())
                .text(component.getText())
                .imageUrl(component.getImageUrl())
                .mediaUrl(component.getImageUrl()) // Same field serves both purposes
                .addSecurityRecommendation(component.getAddSecurityRecommendation())
                .codeExpirationMinutes(component.getCodeExpirationMinutes());

        // Map buttons
        if (component.getButtons() != null && !component.getButtons().isEmpty()) {
            builder.buttons(component.getButtons().stream()
                    .map(this::toButtonDto)
                    .collect(Collectors.toList()));
        }

        // Map carousel cards
        if (component.getCarouselCards() != null && !component.getCarouselCards().isEmpty()) {
            builder.cards(component.getCarouselCards().stream()
                    .map(this::toCarouselCardDto)
                    .collect(Collectors.toList()));
        }

        // Build example from buttons/cards if needed
        builder.example(buildExampleDto(component));

        return builder.build();
    }

    private TemplateComponentButtonDto toButtonDto(TemplateComponentButton button) {
        return TemplateComponentButtonDto.builder()
                .type(button.getType())
                .otpType(button.getOtpType() != null ? button.getOtpType().getValue() : null)
                .phoneNumber(button.getNumber())
                .text(button.getText())
                .url(button.getUrl())
                .index(button.getButtonIndex())
                .autofillText(button.getAutofillText())
                .example(button.getExample())
                .supportedApps(button.getSupportedApps() != null ? 
                        button.getSupportedApps().stream()
                                .map(this::toSupportedApp)
                                .collect(Collectors.toList()) : null)
                .build();
    }

    private TemplateComponentCardsDto toCarouselCardDto(TemplateCarouselCard card) {
        TemplateComponentCardsDto dto = new TemplateComponentCardsDto();
        dto.setIndex(card.getCardIndex());

        // Build components for this card
        List<TemplateCarouselCardComponent> components = buildCarouselCardComponents(card);
        dto.setComponents(components);

        return dto;
    }

    private List<TemplateCarouselCardComponent> buildCarouselCardComponents(TemplateCarouselCard card) {
        List<TemplateCarouselCardComponent> components = new java.util.ArrayList<>();

        // HEADER component
        if (card.getMediaType() != null || card.getImageUrl() != null || card.getHeader() != null) {
            TemplateCarouselCardComponent header = new TemplateCarouselCardComponent();
            header.setType("HEADER");
            header.setFormat(card.getMediaType() != null ? 
                    MediaFormat.fromValue(card.getMediaType()).toString() : MediaFormat.IMAGE.toString());

            // Build example
            TemplateCarouselExample example = new TemplateCarouselExample();
            if (card.getImageUrl() != null) {
                example.setHeaderHandle(List.of(card.getImageUrl()));
            }
            if (card.getHeader() != null) {
                example.setHeaderText(List.of(card.getHeader()));
            }
            header.setExample(example);

            components.add(header);
        }

        // BODY component
        if (card.getBody() != null) {
            TemplateCarouselCardComponent body = new TemplateCarouselCardComponent();
            body.setType("BODY");
            body.setText(card.getBody());
            components.add(body);
        }

        // BUTTONS component
        if (card.getButtons() != null && !card.getButtons().isEmpty()) {
            TemplateCarouselCardComponent buttons = new TemplateCarouselCardComponent();
            buttons.setType("BUTTONS");
            buttons.setButtons(card.getButtons().stream()
                    .map(this::toCarouselButtonDto)
                    .collect(Collectors.toList()));
            components.add(buttons);
        }

        return components;
    }

    private TemplateCarouselButton toCarouselButtonDto(TemplateCarouselCardButton button) {
        TemplateCarouselButton dto = new TemplateCarouselButton();
        dto.setType(button.getType());
        dto.setText(button.getText());
        dto.setUrl(button.getUrl());
        dto.setPhoneNumber(button.getPhoneNumber());
        dto.setIndex(button.getCardButtonIndex());
        return dto;
    }

    private TemplateTextDto toTextDto(TemplateText text) {
        TemplateTextDto dto = new TemplateTextDto();
        dto.setType(text.getType());
        dto.setTextIndex(text.getTextIndex());
        dto.setText(text.getText());
        return dto;
    }
    private SupportedAppDto toSupportedApp(SupportedApp supportedApp){
        SupportedAppDto dto = new  SupportedAppDto();
        dto.setPackageName(supportedApp.getPackageName());
        dto.setSignatureHash(supportedApp.getSignatureHash());
        return dto;
    }

    private TemplateExampleDto buildExampleDto(TemplateComponent component) {
        TemplateExampleDto example = new TemplateExampleDto();

        // Extract header examples if applicable
        if ("HEADER".equalsIgnoreCase(component.getType())) {
            if ("IMAGE".equalsIgnoreCase(component.getFormat()) || 
                "VIDEO".equalsIgnoreCase(component.getFormat()) ||
                "DOCUMENT".equalsIgnoreCase(component.getFormat())) {
                if (component.getImageUrl() != null) {
                    example.setHeaderHandle(List.of(component.getImageUrl()));
                }
            } else if ("TEXT".equalsIgnoreCase(component.getFormat())) {
                if (component.getText() != null) {
                    example.setHeaderText(List.of(component.getText()));
                }
            }
        }

        // Note: bodyText examples would typically come from TemplateText entities
        // which are mapped separately in the texts field

        return example;
    }

    // ==================== ENTITY CREATION HELPERS ====================

    private TemplateComponent toComponent(TemplateComponentRequest req) {
        TemplateComponent comp = TemplateComponent.builder()
                .type(req.getType() != null ? req.getType().toString() : null)
                .format(req.getFormat())
                .text(req.getText())
                .imageUrl(req.getImageUrl() != null ? req.getImageUrl() : req.getMediaUrl())
                .addSecurityRecommendation(req.getAddSecurityRecommendation())
                .codeExpirationMinutes(req.getCodeExpirationMinutes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (req.getButtons() != null) {
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                comp.addButton(toButton(btnReq));
            }
        }

        if (req.getCards() != null) {
            AtomicInteger cardIndex = new AtomicInteger(0);
            for (TemplateComponentCardsRequest cardReq : req.getCards()) {
                comp.addCarouselCard(toCarouselCard(cardReq, cardIndex.getAndIncrement()));
            }
        }

        return comp;
    }

    private TemplateComponentButton toButton(TemplateComponentButtonRequest req) {
        TemplateComponentButton btn = TemplateComponentButton.builder()
                .type(req.getType() != null ? req.getType().getValue() : null)
                .otpType(req.getOtpType())
                .number(req.getPhoneNumber())
                .text(req.getText())
                .url(req.getUrl())
                .buttonIndex(req.getIndex())
                .autofillText(req.getAutofillText())
                .example(req.getExample())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (req.getSupportedApps() != null) {
            for (SupportedAppRequest appReq : req.getSupportedApps()) {
                btn.addSupportedApp(toSupportedApp(appReq));
            }
        }

        return btn;
    }

    private TemplateCarouselCard toCarouselCard(TemplateComponentCardsRequest req, int index) {
        TemplateCarouselCard card = TemplateCarouselCard.builder()
                .cardIndex(index)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (req.getComponents() != null) {
            for (TemplateCarouselCardComponentRequest compReq : req.getComponents()) {
                switch (compReq.getType().toUpperCase()) {
                    case "HEADER" -> {
                        card.setMediaType(compReq.getFormat() != null ? compReq.getFormat().getValue() : null);
                        if (compReq.getExample() != null) {
                            if (compReq.getExample().getHeaderHandle() != null
                                    && !compReq.getExample().getHeaderHandle().isEmpty()) {
                                card.setImageUrl(compReq.getExample().getHeaderHandle().get(0));
                            }
                            if (compReq.getExample().getHeaderText() != null
                                    && !compReq.getExample().getHeaderText().isEmpty()) {
                                card.setHeader(compReq.getExample().getHeaderText().get(0));
                            }
                        }
                    }
                    case "BODY" -> card.setBody(compReq.getText());
                    case "BUTTONS" -> {
                        if (compReq.getButtons() != null) {
                            for (TemplateCarouselButtonRequest btnReq : compReq.getButtons()) {
                                card.addButton(toCarouselCardButton(btnReq));
                            }
                        }
                    }
                }
            }
        }

        return card;
    }

    private TemplateCarouselCardButton toCarouselCardButton(TemplateCarouselButtonRequest req) {
        return TemplateCarouselCardButton.builder()
                .type(req.getType() != null ? req.getType().getValue() : null)
                .text(req.getText())
                .url(req.getUrl())
                .phoneNumber(req.getPhoneNumber())
                .cardButtonIndex(req.getIndex())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private TemplateText toText(TemplateTextRequest req) {
        return TemplateText.builder()
                .type(req.getType())
                .text(req.getText())
                .isCarousel(req.isCarousel())
                .textIndex(req.getTextIndex())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SupportedApp toSupportedApp(SupportedAppRequest req) {
        return SupportedApp.builder()
                .packageName(req.getPackageName())
                .signatureHash(req.getSignatureHash())
                .build();
    }

    private SupportedAppRequest toSupportedAppRequest(SupportedApp app) {
        SupportedAppRequest req = new SupportedAppRequest();
        req.setPackageName(app.getPackageName());
        req.setSignatureHash(app.getSignatureHash());
        return req;
    }
}