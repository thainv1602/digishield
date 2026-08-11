package com.digishield.ai.application;

import com.digishield.ai.domain.Difficulty;
import com.digishield.ai.domain.TemplateChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lure the stub generates, per channel.
 *
 * <p>This is what an air-gapped deployment sends to real employees when no AI
 * provider is reachable, so "it returns something" is not enough: an SMS lure
 * carrying an email's paragraphs, or a USB pretext offering a link to click,
 * is a broken simulation that still looks like it worked.
 */
class StubAiClientGenerateTest {

    private final StubAiClient client = new StubAiClient();

    @ParameterizedTest
    @EnumSource(TemplateChannel.class)
    @DisplayName("every channel produces a complete template")
    void everyChannelIsAnswered(TemplateChannel channel) {
        var generated = client.generate(channel, "ngân hàng", "Tết");

        assertThat(generated.subject()).isNotBlank();
        assertThat(generated.body()).isNotBlank();
        assertThat(generated.bodyRef()).isNotBlank();
        assertThat(generated.difficulty()).isNotNull();
    }

    @Test
    @DisplayName("a short-message channel gets a short lure, not an email body")
    void smsDoesNotCarryAnEmailBody() {
        var sms = client.generate(TemplateChannel.SMS, "ngân hàng", null);
        var email = client.generate(TemplateChannel.EMAIL, "ngân hàng", null);

        assertThat(sms.body()).doesNotContain("Kính gửi").doesNotContain("\n\n");
        assertThat(sms.body()).contains("ngân hàng");
        // The email version is the one with salutation and paragraphs.
        assertThat(email.body()).contains("Kính gửi").contains("Trân trọng");
    }

    @Test
    @DisplayName("the voice channel yields a call script, and USB an attachment pretext")
    void nonLinkChannelsDoNotOfferALink() {
        var voice = client.generate(TemplateChannel.VOICE, "bảo hiểm", null);
        var usb = client.generate(TemplateChannel.USB, "bảo hiểm", null);

        assertThat(voice.body()).contains("Kịch bản gọi");
        assertThat(usb.body()).contains(".docx");
        // Neither is a channel a victim can click, so neither should carry a URL.
        assertThat(voice.body()).doesNotContain("https://");
        assertThat(usb.body()).doesNotContain("https://");
    }

    @Test
    @DisplayName("a season reaches the subject; its absence leaves no empty gap")
    void seasonIsOptional() {
        var withSeason = client.generate(TemplateChannel.EMAIL, "ngân hàng", "Tết");
        var withoutSeason = client.generate(TemplateChannel.EMAIL, "ngân hàng", "  ");

        assertThat(withSeason.subject()).contains("mùa Tết");
        assertThat(withoutSeason.subject()).doesNotContain("mùa").doesNotContain("  ");
    }

    @Test
    @DisplayName("a missing industry falls back rather than producing a blank")
    void industryFallsBack() {
        var generated = client.generate(TemplateChannel.EMAIL, "   ", null);

        assertThat(generated.subject()).contains("doanh nghiệp");
        assertThat(generated.body()).contains("doanh nghiệp");
    }

    @Test
    @DisplayName("difficulty is stable for the same input, so a campaign does not shift under it")
    void difficultyIsDeterministic() {
        Difficulty first = client.generate(TemplateChannel.EMAIL, "ngân hàng", null).difficulty();
        Difficulty again = client.generate(TemplateChannel.EMAIL, "ngân hàng", null).difficulty();

        assertThat(again).isEqualTo(first);
    }

    @Test
    @DisplayName("the link slug carries no spaces or diacritics")
    void theLinkIsUsable() {
        var generated = client.generate(TemplateChannel.EMAIL, "Ngân Hàng ACB", null);

        String url = generated.body().substring(generated.body().indexOf("https://"));
        assertThat(url.split("\\s")[0]).matches("https://xac-minh-[a-z0-9-]+\\.example\\.vn");
    }
}
