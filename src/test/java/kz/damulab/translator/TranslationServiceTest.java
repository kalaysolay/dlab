package kz.damulab.translator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import kz.damulab.ai.AiProvider;
import kz.damulab.ai.AiProviderException;
import kz.damulab.ai.AiTextResult;
import kz.damulab.ai.AiTranslationExplanationMode;
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
        TranslationService service = new TranslationService(aiProvider, mock(TranslationHistoryService.class));

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
        TranslationService service = new TranslationService(aiProvider, mock(TranslationHistoryService.class));

        service.explain(new TranslationExplanationRequest(
                TranslationDirection.KAZAKH_TO_RUSSIAN, " Сәлем ", " Привет ", true),
                Locale.forLanguageTag("kk"));

        ArgumentCaptor<AiTranslationExplanationRequest> captor =
                ArgumentCaptor.forClass(AiTranslationExplanationRequest.class);
        verify(aiProvider).explainTranslation(captor.capture());
        assertThat(captor.getValue().explanationLanguage()).isEqualTo("Kazakh");
        assertThat(captor.getValue().sourceText()).isEqualTo("Сәлем");
        assertThat(captor.getValue().translatedText()).isEqualTo("Привет");
        assertThat(captor.getValue().explanationMode()).isEqualTo(AiTranslationExplanationMode.ECONOMY);
    }

    @Test
    void usesDetailedExplanationWhenEconomyModeIsDisabled() {
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.explainTranslation(any())).thenReturn(new AiTextResult("stub", "stub", "Разбор"));
        TranslationService service = new TranslationService(aiProvider, mock(TranslationHistoryService.class));

        service.explain(new TranslationExplanationRequest(
                TranslationDirection.RUSSIAN_TO_KAZAKH, "Привет", "Сәлем", false),
                Locale.forLanguageTag("ru"));

        ArgumentCaptor<AiTranslationExplanationRequest> captor =
                ArgumentCaptor.forClass(AiTranslationExplanationRequest.class);
        verify(aiProvider).explainTranslation(captor.capture());
        assertThat(captor.getValue().explanationMode()).isEqualTo(AiTranslationExplanationMode.DETAILED);
    }

    @Test
    void recordsAttemptBeforeCallingProviderAndCompletesWithActualResult() {
        AiProvider provider = mock(AiProvider.class);
        TranslationHistoryService history = mock(TranslationHistoryService.class);
        var result = new AiTextResult("deepseek", "test-model", "Благополучие");
        when(history.start(TranslationDirection.KAZAKH_TO_RUSSIAN, "Ырыс", null, null, null))
                .thenReturn(42L);
        when(provider.translate(any())).thenReturn(result);

        new TranslationService(provider, history).translate(
                new TranslationRequest(TranslationDirection.KAZAKH_TO_RUSSIAN, " Ырыс "));

        var order = org.mockito.Mockito.inOrder(history, provider);
        order.verify(history).start(TranslationDirection.KAZAKH_TO_RUSSIAN, "Ырыс", null, null, null);
        order.verify(provider).translate(any());
        order.verify(history).succeed(42L, result);
    }

    @Test
    void explanationFailureIsRecordedAndOriginalExceptionIsPreserved() {
        AiProvider provider = mock(AiProvider.class);
        TranslationHistoryService history = mock(TranslationHistoryService.class);
        var failure = new AiProviderException("provider_unavailable", "private external error");
        when(history.start(TranslationDirection.KAZAKH_TO_RUSSIAN, "Ырыс", "Благополучие", "Russian", "ECONOMY"))
                .thenReturn(43L);
        when(provider.explainTranslation(any())).thenThrow(failure);

        assertThatThrownBy(() -> new TranslationService(provider, history).explain(
                new TranslationExplanationRequest(TranslationDirection.KAZAKH_TO_RUSSIAN,
                        "Ырыс", "Благополучие", true), Locale.forLanguageTag("ru")))
                .isSameAs(failure);
        verify(history).fail(43L, "provider_unavailable");
        org.mockito.Mockito.verify(history, org.mockito.Mockito.never()).succeed(any(), any());
    }
}
