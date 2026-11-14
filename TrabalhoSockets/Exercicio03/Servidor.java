package Exercicio03;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Servidor do jogo da forca (1 jogador por partida).
 *
 * Protocolo (texto, uma mensagem por linha):
 *   S: TAMANHO <n>                     ao conectar, informa o tamanho da palavra
 *   C: LETRA <x>                       palpite do jogador
 *   S: ACERTO <x> <p1,p2,...>          posições (a partir de 1) onde a letra aparece
 *   S: ERRO <parte do boneco>          parte do boneco que foi desenhada
 *   S: REPETIDA <x> | INVALIDA         palpite ignorado
 *   S: FIM VITORIA <palavra> | FIM DERROTA <palavra>   (logo após a jogada que encerra o jogo)
 */
public class Servidor {

    static final String[] PALAVRAS = {
            "SOCKET", "SERVIDOR", "CLIENTE", "PROTOCOLO", "THREAD", "MULTICAST",
            "DISTRIBUIDO", "MENSAGEM", "CONEXAO", "DATAGRAMA", "ROTEADOR", "PACOTE"
    };
    static final String[] PARTES = {
            "cabeça", "tronco", "braço esquerdo", "braço direito", "perna esquerda", "perna direita"
    };

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5003;
        Random random = new Random();

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor da forca ouvindo na porta " + porta);
            while (true) {
                try (Socket socket = servidor.accept()) {
                    String palavra = PALAVRAS[random.nextInt(PALAVRAS.length)];
                    jogar(socket, palavra);
                } catch (IOException e) {
                    System.out.println("Erro na partida: " + e.getMessage());
                }
            }
        }
    }

    static void jogar(Socket socket, String palavra) throws IOException {
        String quem = socket.getRemoteSocketAddress().toString();
        System.out.println("\n[+] Nova partida com " + quem + " | palavra: " + palavra);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        Set<Character> tentadas = new LinkedHashSet<>();
        int erros = 0;
        int acertos = 0; // quantidade de posições já reveladas

        out.println("TAMANHO " + palavra.length());

        String linha;
        while ((linha = in.readLine()) != null) {
            String[] p = linha.trim().split("\\s+");
            if (p.length != 2 || !p[0].equalsIgnoreCase("LETRA") || p[1].length() != 1
                    || !Character.isLetter(p[1].charAt(0))) {
                out.println("INVALIDA");
                continue;
            }
            char letra = Character.toUpperCase(p[1].charAt(0));
            if (!tentadas.add(letra)) {
                out.println("REPETIDA " + letra);
                continue;
            }

            List<String> posicoes = new ArrayList<>();
            for (int i = 0; i < palavra.length(); i++) {
                if (palavra.charAt(i) == letra) {
                    posicoes.add(String.valueOf(i + 1));
                }
            }

            if (posicoes.isEmpty()) {
                String parte = PARTES[erros++];
                out.println("ERRO " + parte);
                System.out.println("    " + letra + " -> errou (" + parte + ")");
            } else {
                acertos += posicoes.size();
                out.println("ACERTO " + letra + " " + String.join(",", posicoes));
                System.out.println("    " + letra + " -> acertou nas posições " + posicoes);
            }

            if (acertos == palavra.length()) {
                out.println("FIM VITORIA " + palavra);
                System.out.println("[-] Jogador venceu");
                return;
            }
            if (erros == PARTES.length) {
                out.println("FIM DERROTA " + palavra);
                System.out.println("[-] Jogador perdeu");
                return;
            }
        }
        System.out.println("[-] Jogador desistiu");
    }
}
