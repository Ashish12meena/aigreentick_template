package com.aigreentick.services.template.mapper;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.build.TemplateDto;
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
                .waId(fbTemplate.getMetaTemplateId())  // Store Facebook template ID
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Map components from Facebook response
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

        // Map components
        if (req.getComponents() != null) {
            for (TemplateComponentRequest compReq : req.getComponents()) {
                template.addComponent(toComponent(compReq));
            }
        }

        // Map template-level texts/variables
        if (request.getVariables() != null) {
            for (TemplateTextRequest textReq : request.getVariables()) {
                template.addText(toText(textReq));
            }
        }

        return template;
    }

    //convert enity to convertable sendable dto 
    public TemplateDto toTemplateDto(Template template){
        TemplateDto dto = new TemplateDto();
        return dto;

    }

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

        // Map buttons
        if (req.getButtons() != null) {
            for (TemplateComponentButtonRequest btnReq : req.getButtons()) {
                comp.addButton(toButton(btnReq));
            }
        }

        // Map carousel cards
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

        // Map supported apps
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
}