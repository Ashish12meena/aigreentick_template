package com.aigreentick.services.template.service.impl;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.request.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;

@Service
public class SendTemplateOrchestratorServiceImpl {

    public TemplateResponseDto broadcastTemplate(SendTemplateRequestDto request, Long userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'broadcastTemplate'");
    }

        /**
     * Fetches all required dependencies asynchronously.
     */
    // private CampaignDependencies fetchDependenciesAsync(SendTemplateRequestDto dto, Long userId) {
    //     CompletableFuture<Template> templateFuture = CompletableFuture
    //             .supplyAsync(() -> templateService.findById(dto.getTemlateId()));
    //     CompletableFuture<CountryDto> countryFuture = CompletableFuture
    //             .supplyAsync(() -> countryService.findById(dto.getCountryId()));
    //     CompletableFuture<AccessTokenCredentials> credentialsFuture = CompletableFuture
    //             .supplyAsync(() -> userService.getPhoneNumberIdAccessToken(userId));

    //     return new CampaignDependencies(
    //             templateFuture.join(),
    //             countryFuture.join(),
    //             credentialsFuture.join()
    //     );
    // }

    //   /**
    //  * Holds all async-fetched dependencies.
    //  */
    // private record CampaignDependencies(
    //         Template template,
    //         Country country,
    //         AccessTokenCredentials credentials) {
    // }
    
}
