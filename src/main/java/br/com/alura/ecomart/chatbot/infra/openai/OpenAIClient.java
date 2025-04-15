package br.com.alura.ecomart.chatbot.infra.openai;

import br.com.alura.ecomart.chatbot.domain.DadosCalculoFrete;
import br.com.alura.ecomart.chatbot.domain.service.CalculadorDeFrete;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAIClient {

    private final OpenAiService service;
    private final CalculadorDeFrete calculadorDeFrete;
    private final List<ChatMessage> historico = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIClient(
            @Value("${app.openai.api.key}") String apiKey,
            CalculadorDeFrete calculadorDeFrete) {
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.calculadorDeFrete = calculadorDeFrete;
    }

    public String enviarRequisicaoChatCompletion(DadosRequisicaoChatCompletion dados) {
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), dados.promptUsuario());
        historico.add(userMessage);

        // Verifica se é um pedido de frete
        if (dados.promptUsuario().toLowerCase().contains("frete")) {
            try {
                // Prompt para extrair JSON com os dados do frete
                List<ChatMessage> extracao = new ArrayList<>();
                extracao.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                        "Extraia os dados de frete no seguinte formato JSON:\n" +
                        "{ \"quantidadeProdutos\": 3, \"uf\": \"MG\" }"));
                extracao.add(new ChatMessage(ChatMessageRole.USER.value(), dados.promptUsuario()));

                ChatCompletionRequest extracaoRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(extracao)
                        .temperature(0.0)
                        .build();

                ChatCompletionResult extracaoResult = service.createChatCompletion(extracaoRequest);
                String jsonExtraido = extracaoResult.getChoices().get(0).getMessage().getContent();

                // Converte o JSON retornado para o record DadosCalculoFrete
                DadosCalculoFrete dadosFrete = objectMapper.readValue(jsonExtraido, DadosCalculoFrete.class);
                BigDecimal valorFrete = calculadorDeFrete.calcular(dadosFrete);

                String resposta = "O valor do frete para o estado " + dadosFrete.uf() +
                        " com " + dadosFrete.quantidadeProdutos() + " produto(s) é R$ " + valorFrete;

                ChatMessage respostaFrete = new ChatMessage(ChatMessageRole.ASSISTANT.value(), resposta);
                historico.add(respostaFrete);
                return resposta;

            } catch (Exception e) {
                String erro = "Não foi possível calcular o frete. Certifique-se de informar a quantidade de produtos e o estado de destino.";
                historico.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), erro));
                return erro;
            }
        }

        // Caso não seja um pedido de frete, responde normalmente
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(historico)
                .temperature(0.7)
                .build();

        ChatCompletionResult result = service.createChatCompletion(request);
        String resposta = result.getChoices().get(0).getMessage().getContent();
        historico.add(result.getChoices().get(0).getMessage());

        return resposta;
    }

    public List<String> carregarHistoricoDeMensagens() {
        List<String> mensagens = new ArrayList<>();
        for (ChatMessage msg : historico) {
            mensagens.add(msg.getRole() + ": " + msg.getContent());
        }
        return mensagens;
    }

    public void apagarHistorico() {
        historico.clear();
    }
}
