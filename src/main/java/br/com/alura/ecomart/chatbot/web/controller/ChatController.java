package br.com.alura.ecomart.chatbot.web.controller;

import br.com.alura.ecomart.chatbot.domain.service.ChatbotService;
import br.com.alura.ecomart.chatbot.web.dto.PerguntaDto;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping({"/", "chat"})
public class ChatController {

    private static final String PAGINA_CHAT = "chat";

    private final ChatbotService service;

    public ChatController(ChatbotService service) {
        this.service = service;
    }

    @GetMapping
    public String carregarPaginaChatbot(Model model) {
        // Carrega o histórico de mensagens
        var mensagens = service.carregarHistorico();
        model.addAttribute("historico", mensagens);
        return PAGINA_CHAT;
    }

    @PostMapping
    @ResponseBody
    public String responderPergunta(@RequestBody PerguntaDto dto) {
        return service.responderPergunta(dto.pergunta());
    }

    @GetMapping("limpar")
    public String limparConversa() {
        // Limpa o histórico de mensagens
        service.limparHistorico();
        // Redireciona para a página de chat com o histórico limpo
        return "redirect:/chat";
    }
}
