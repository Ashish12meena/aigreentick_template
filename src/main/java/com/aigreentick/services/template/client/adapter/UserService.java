package com.aigreentick.services.template.client.adapter;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.response.AccessTokenCredentials;

@Component
public class UserService {

    public AccessTokenCredentials getWabaAccessToken() {
        return new AccessTokenCredentials("1436853954305849",
                "EAAOcfziRygMBPGSZCjTEADbcIXleBDVHuZAF61EDXn6qw2GuS6ghjiVHESlosKbAFGEAGMkArSBqyyyaqUxS51dSiLFtZBRd0oEZAY1LiNElHPcM3bsRzqNjaQZAXht6WOKuEWEGfotJASpCGqMOKBrXUMQr03TopqfrZCBe4xrmlfwVipb6dYQaVkmn8gCqzN");
    }

}
