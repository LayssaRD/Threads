package Exercicio05;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sistema central da rede de lojas. Recebe, ao mesmo tempo, os dados de n filiais
 * (uma thread por filial) e consolida as compras e vendas do dia.
 *
 * Protocolo (texto, campos separados por ';'):
 *   FILIAL;<id>;<ocorrências previstas>
 *   COMPRA;<produto>;<quantidade>;<valor unitário em centavos>
 *   VENDA;<produto>;<quantidade>;<valor unitário em centavos>
 *   FIM;<total de ocorrências enviadas>
 * Ao receber FIM a central responde:
 *   OK;<compras>;<vendas>;<total compras>;<total vendas>   (centavos)
 *
 * Uso: java Exercicio05.Central [filiais esperadas = 5] [porta = 5005]
 * Quando todas as filiais esperadas terminam, imprime o relatório consolidado e encerra.
 */
public class Central {

    static class Resumo {
        int previstas;
        int recebidas;
        int compras;
        int vendas;
        long totalCompras;
        long totalVendas;
        boolean concluida;
    }

    static final Map<Integer, Resumo> filiais = new TreeMap<>();
    static int esperadas;
    static int concluidas;

    public static void main(String[] args) throws IOException, InterruptedException {
        esperadas = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5005;

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Central ouvindo na porta " + porta + ", aguardando " + esperadas + " filiais...");
            Thread aceitador = new Thread(() -> {
                while (true) {
                    try {
                        Socket s = servidor.accept();
                        new Thread(() -> atender(s)).start();
                    } catch (IOException e) {
                        return; // servidor fechado
                    }
                }
            });
            aceitador.setDaemon(true);
            aceitador.start();

            synchronized (filiais) {
                while (concluidas < esperadas) {
                    filiais.wait();
                }
            }
        }
        imprimirRelatorio();
    }

    static void atender(Socket socket) {
        Resumo r = null;
        int id = -1;
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String linha;
            while ((linha = in.readLine()) != null) {
                String[] c = linha.split(";");
                switch (c[0]) {
                    case "FILIAL" -> {
                        id = Integer.parseInt(c[1]);
                        r = new Resumo();
                        r.previstas = Integer.parseInt(c[2]);
                        synchronized (filiais) {
                            filiais.put(id, r);
                        }
                        log("Filial " + id + " conectada (" + r.previstas + " ocorrências previstas)");
                    }
                    case "COMPRA", "VENDA" -> {
                        int qtd = Integer.parseInt(c[2]);
                        long valor = qtd * Long.parseLong(c[3]);
                        synchronized (filiais) {
                            r.recebidas++;
                            if (c[0].equals("COMPRA")) {
                                r.compras++;
                                r.totalCompras += valor;
                            } else {
                                r.vendas++;
                                r.totalVendas += valor;
                            }
                        }
                        if (r.recebidas % 300 == 0) {
                            log(String.format("Filial %d: %4d/%d ocorrências recebidas", id, r.recebidas, r.previstas));
                        }
                    }
                    case "FIM" -> {
                        int enviadas = Integer.parseInt(c[1]);
                        synchronized (filiais) {
                            r.concluida = true;
                            out.println("OK;" + r.compras + ";" + r.vendas + ";" + r.totalCompras + ";" + r.totalVendas);
                            log("Filial " + id + " concluiu: " + r.recebidas + " recebidas / " + enviadas + " enviadas"
                                    + (r.recebidas == enviadas ? " (conferido)" : " (DIVERGENTE!)"));
                            concluidas++;
                            filiais.notifyAll();
                        }
                        return;
                    }
                    default -> log("Linha ignorada: " + linha);
                }
            }
        } catch (IOException | RuntimeException e) {
            log("Erro na filial " + id + ": " + e.getMessage());
        }
    }

    static void imprimirRelatorio() {
        System.out.println("\n=================== RELATÓRIO CONSOLIDADO DO DIA ===================");
        System.out.printf("%-8s %8s %8s %16s %16s %16s%n", "Filial", "Compras", "Vendas", "Total compras", "Total vendas", "Resultado");
        int compras = 0, vendas = 0;
        long tc = 0, tv = 0;
        for (Map.Entry<Integer, Resumo> e : filiais.entrySet()) {
            Resumo r = e.getValue();
            System.out.printf("%-8d %8d %8d %16s %16s %16s%n", e.getKey(), r.compras, r.vendas,
                    reais(r.totalCompras), reais(r.totalVendas), reais(r.totalVendas - r.totalCompras));
            compras += r.compras;
            vendas += r.vendas;
            tc += r.totalCompras;
            tv += r.totalVendas;
        }
        System.out.println("--------------------------------------------------------------------");
        System.out.printf("%-8s %8d %8d %16s %16s %16s%n", "TOTAL", compras, vendas, reais(tc), reais(tv), reais(tv - tc));
        System.out.println("Ocorrências processadas: " + (compras + vendas));
    }

    static synchronized void log(String s) {
        System.out.println("[central] " + s);
    }

    static String reais(long centavos) {
        String sinal = centavos < 0 ? "-" : "";
        long abs = Math.abs(centavos);
        return String.format(Locale.forLanguageTag("pt-BR"), "%sR$ %,d,%02d", sinal, abs / 100, abs % 100);
    }
}
