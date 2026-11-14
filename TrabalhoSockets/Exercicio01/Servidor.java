package Exercicio01;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Servidor de Fortunes (biscoito da sorte).
 *
 * Protocolo em texto, um comando por linha:
 *   GET-FORTUNE                      -> OK <frase aleatória>
 *   ADD-FORTUNE <nova frase>         -> OK <mensagem>
 *   UPD-FORTUNE <pos> <nova frase>   -> OK <mensagem>   (pos começa em 1)
 *   LST-FORTUNE                      -> OK <n>  seguido de n linhas "pos - frase"
 * Em caso de problema a resposta começa com ERRO.
 *
 * As frases ficam salvas em fortunes.txt para não se perderem ao reiniciar.
 */
public class Servidor {

    static final Path ARQUIVO = Paths.get("fortunes.txt");
    static final List<String> frases = new ArrayList<>();
    static final Random random = new Random();

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5001;
        carregar();

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor de Fortunes ouvindo na porta " + porta
                    + " (" + frases.size() + " frases carregadas)");
            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> atender(cliente)).start();
            }
        }
    }

    static void atender(Socket socket) {
        String quem = socket.getRemoteSocketAddress().toString();
        System.out.println("[+] Cliente conectado: " + quem);
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String linha;
            while ((linha = in.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) {
                    continue;
                }
                System.out.println("    " + quem + " -> " + linha);
                processar(linha, out);
            }
        } catch (IOException e) {
            System.out.println("    Erro com " + quem + ": " + e.getMessage());
        }
        System.out.println("[-] Cliente desconectado: " + quem);
    }

    static void processar(String linha, PrintWriter out) {
        String[] partes = linha.split("\\s+", 2);
        String comando = partes[0].toUpperCase();
        String resto = partes.length > 1 ? partes[1].trim() : "";

        synchronized (frases) {
            switch (comando) {
                case "GET-FORTUNE" -> {
                    if (frases.isEmpty()) {
                        out.println("ERRO nenhuma frase cadastrada");
                    } else {
                        out.println("OK " + frases.get(random.nextInt(frases.size())));
                    }
                }
                case "ADD-FORTUNE" -> {
                    if (resto.isEmpty()) {
                        out.println("ERRO uso: ADD-FORTUNE <nova frase>");
                    } else {
                        frases.add(resto);
                        salvar();
                        out.println("OK frase adicionada na posição " + frases.size());
                    }
                }
                case "UPD-FORTUNE" -> {
                    String[] p = resto.split("\\s+", 2);
                    int pos;
                    try {
                        pos = Integer.parseInt(p[0]);
                    } catch (NumberFormatException e) {
                        out.println("ERRO uso: UPD-FORTUNE <pos> <nova frase>");
                        return;
                    }
                    if (p.length < 2 || p[1].isBlank()) {
                        out.println("ERRO uso: UPD-FORTUNE <pos> <nova frase>");
                    } else if (pos < 1 || pos > frases.size()) {
                        out.println("ERRO posição inválida, use de 1 a " + frases.size());
                    } else {
                        frases.set(pos - 1, p[1].trim());
                        salvar();
                        out.println("OK frase " + pos + " alterada");
                    }
                }
                case "LST-FORTUNE" -> {
                    out.println("OK " + frases.size());
                    for (int i = 0; i < frases.size(); i++) {
                        out.println((i + 1) + " - " + frases.get(i));
                    }
                }
                default -> out.println("ERRO comando desconhecido: " + comando);
            }
        }
    }

    static void carregar() throws IOException {
        if (Files.exists(ARQUIVO)) {
            for (String l : Files.readAllLines(ARQUIVO, StandardCharsets.UTF_8)) {
                if (!l.isBlank()) {
                    frases.add(l.trim());
                }
            }
        }
        if (frases.isEmpty()) {
            frases.add("A sorte favorece os que estão preparados.");
            frases.add("Uma longa jornada começa com um único passo.");
            frases.add("Quem não arrisca, não petisca.");
            frases.add("Grandes coisas nunca vêm de zonas de conforto.");
            frases.add("O sucesso é a soma de pequenos esforços repetidos dia após dia.");
            salvar();
        }
    }

    static void salvar() {
        try {
            Files.write(ARQUIVO, frases, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Não foi possível salvar " + ARQUIVO + ": " + e.getMessage());
        }
    }
}
