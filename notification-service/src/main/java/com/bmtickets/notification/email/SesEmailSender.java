package com.bmtickets.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** Amazon SES v2. Credentials come from IRSA (pod service account -> IAM role), never from static keys. */
@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private final SesV2Client ses;
    private final String from;
    private final String configurationSet;

    public SesEmailSender(@Value("${app.notification.aws-region}") String region,
                          @Value("${app.notification.from-address}") String from,
                          @Value("${app.notification.ses-configuration-set:}") String configurationSet) {
        this.ses = SesV2Client.builder().region(Region.of(region)).build();
        this.from = from;
        this.configurationSet = configurationSet;
    }

    @Override
    public String send(String to, String subject, String html, String text) {
        SendEmailRequest.Builder request = SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(html).charset("UTF-8").build())
                                .text(Content.builder().data(text).charset("UTF-8").build())
                                .build())
                        .build()).build());
        if (!configurationSet.isBlank()) {
            request.configurationSetName(configurationSet);   // bounce/complaint tracking via SNS
        }
        return ses.sendEmail(request.build()).messageId();
    }
}
