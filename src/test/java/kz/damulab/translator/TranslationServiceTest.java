package kz.damulab.translator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import kz.damulab.ai.AiProvider;
import kz.damulab.ai.AiTextResult;
import kz.damulab.ai.AiTranslationExplanationRequest;
import kz.damulab.ai.AiTranslationRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Проверяет точное преобразование направления и локали в AI-контракт. */
class TranslationServiceTest {

    @Test
    void mapsRussianToKazakhAndTrimsInput() {
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.translate(any())).thenReturn(new AiTextResult("stub", "stub", "Сәлем"));
        TranslationService service = new TranslationService(aiProvider);

        TranslationResponse response = service.translate(new TranslationRequest(
                TranslationDirection.RUSSIAN_TO_KAZAKH, "  Привет  "));

        ArgumentCaptor<AiTranslationRequest> captor = ArgumentCaptor.forClass(AiTranslationRequest.class);
        verify(aiProvider).translate(captor.capture());
        assertThat(captor.getValue().sourceLanguage()).isEqualTo("Russian");
        assertThat(captor.getValue().targetLanguage()).isEqualTo("Kazakh");
        assertThat(captor.getValue().text()).isEqualTo("Привет");
        assertThat(response.text()).isEqualTo("Сәлем");
    }

    @Test
    void asksForKazakhExplanationWhenInterfaceLocaleIsKazakh() {
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.explainTranslation(any())).thenReturn(new AiTextResult("stub", "stub", "Талдау"));
        TranslationService service = new TranslationService(aiProvider);

        service.explain(new TranslationExplanationRequest(
                TranslationDirection.KAZAKH_TO_RUSSIAN, " Сәлем ", " Привет "),
                Locale.forLanguageTag("kk"));

        ArgumentCaptor<AiTranslationExplanationRequest> captor =
                ArgumentCaptor.forClass(AiTranslationExplanationRequest.class);
        verify(aiProvider).explainTranslation(captor.capture());
        assertThat(captor.getValue().explanationLanguage()).isEqualTo("Kazakh");
        assertThat(captor.getValue().sourceText()).isEqualTo("Сәлем");
        assertThat(captor.getValue().translatedText()).isEqualTo("Привет");
    }
}
