package Exercicio05;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Uma filial: simula as compras e vendas do dia e envia cada ocorrência para a central.
 *
 * Uso: java Exercicio05.Filial <id> [ocorrências = 1500] [delay máx. ms = 5] [host = localhost] [porta = 5005]
 */
public class Filial implements Runnable {

    static final String[] PRODUTOS = {"Camiseta", "Calça", "Tênis", "Notebook", "Celular", "Fone",
            "Panela", "Toalha", "Livro", "Mochila", "Relógio", "Cafeteira"};
    static final long[] PRECOS = {4990, 12990, 29990, 349900, 189900, 15990,
            8990, 3990, 5990, 14990, 25990, 19990}; // preço de venda em centavos

    final int id;
    final int ocorrencias;
    final int delayMax;
    final String host;
    final int porta;

    Filial(int id, int ocorrencias, int delayMax, String host, int porta) {
        this.id = id;
        this.ocorrencias = ocorrencias;
        this.delayMax = delayMax;
        this.host = host;
        this.porta = porta;
    }

    public static void main(String[] args) {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int ocorrencias = args.length > 1 ? Integer.parseInt(args[1]) : 1500;
        int delay = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        String host = args.length > 3 ? args[3] : "localhost";
        int porta = args.length > 4 ? Integer.parseInt(args[4]) : 5005;
        new Filial(id, ocorrencias, delay, host, porta).run();
    }

    @Override
    public void run() {
        Random random = new Random();
        int compras = 0, vendas = 0;
        long totalCompras = 0, totalVendas = 0;

        try (Socket socket = new Socket(host, porta);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("FILIAL;" + id + ";" + ocorrencias);
            System.out.println("[filial " + id + "] conectada, enviando " + ocorrencias + " ocorrências...");

            for (int i = 1; i <= ocorrencias; i++) {
                int p = random.nextInt(PRODUTOS.length);
                int qtd = 1 + random.nextInt(5);
                if (random.nextInt(100) < 35) {
                    // compra de fornecedor: custo ~60% do preço de venda
                    long custo = PRECOS[p] * 60 / 100;
                    out.println("COMPRA;" + PRODUTOS[p] + ";" + qtd + ";" + custo);
                    compras++;
                    totalCompras += qtd * custo;
                } else {
                    out.println("VENDA;" + PRODUTOS[p] + ";" + qtd + ";" + PRECOS[p]);
                    vendas++;
                    totalVendas += qtd * PRECOS[p];
                }
                if (delayMax > 0) {
                    Thread.sleep(1 + random.nextInt(delayMax)); // tempo de cada ocorrência
                }
            }
            out.println("FIM;" + ocorrencias);

            String[] r = in.readLine().split(";");
            boolean confere = Integer.parseInt(r[1]) == compras && Integer.parseInt(r[2]) == vendas
                    && Long.parseLong(r[3]) == totalCompras && Long.parseLong(r[4]) == totalVendas;
            System.out.println("[filial " + id + "] dia encerrado: " + compras + " compras, " + vendas
                    + " vendas. Central " + (confere ? "confirmou os mesmos totais." : "retornou totais DIFERENTES!"));
        } catch (IOException | InterruptedException e) {
            System.out.println("[filial " + id + "] erro: " + e.getMessage());
        }
    }
}
