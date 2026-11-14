package Exercicio04;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor de uma instituição financeira com n clientes simultâneos (uma thread por conexão).
 *
 * Protocolo (texto):
 *   LOGIN <conta>       -> OK ...  (a conta é criada com saldo 0 se não existir)
 *   DEPOSITO <valor>    -> OK ... / ERRO ...
 *   SAQUE <valor>       -> OK ... / ERRO ...
 *   SALDO               -> OK ...
 *   SAIR                -> OK ... e a conexão é fechada
 * O servidor também envia, a qualquer momento, linhas "AVISO ..." quando outro terminal
 * conectado na mesma conta altera o saldo (atualização on-line).
 *
 * Sem conflitos: cada operação acontece dentro de synchronized(conta), então dois terminais
 * nunca alteram o mesmo saldo ao mesmo tempo.
 */
public class Servidor {

    static class Conta {
        final String numero;
        private long saldoCentavos;
        final Set<PrintWriter> terminais = ConcurrentHashMap.newKeySet();

        Conta(String numero, long saldoInicial) {
            this.numero = numero;
            this.saldoCentavos = saldoInicial;
        }
    }

    static final ConcurrentHashMap<String, Conta> contas = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5004;
        contas.put("1001", new Conta("1001", 100_000)); // R$ 1.000,00
        contas.put("1002", new Conta("1002", 50_000));  // R$ 500,00
        contas.put("1003", new Conta("1003", 0));

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor do banco ouvindo na porta " + porta);
            System.out.println("Contas iniciais: 1001 (R$ 1.000,00), 1002 (R$ 500,00), 1003 (R$ 0,00)");
            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> atender(cliente)).start();
            }
        }
    }

    static void atender(Socket socket) {
        String quem = socket.getRemoteSocketAddress().toString();
        Conta conta = null;
        PrintWriter out = null;
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            System.out.println("[+] Terminal conectado: " + quem);

            // 1) login
            String linha;
            while (conta == null && (linha = in.readLine()) != null) {
                String[] p = linha.trim().split("\\s+");
                if (p.length == 2 && p[0].equalsIgnoreCase("LOGIN") && p[1].matches("\\d{1,10}")) {
                    boolean nova = !contas.containsKey(p[1]);
                    conta = contas.computeIfAbsent(p[1], n -> new Conta(n, 0));
                    conta.terminais.add(out);
                    int abertos;
                    long saldo;
                    synchronized (conta) {
                        abertos = conta.terminais.size();
                        saldo = conta.saldoCentavos;
                    }
                    enviar(out, "OK " + (nova ? "Conta " + conta.numero + " criada. " : "Bem-vindo à conta " + conta.numero + ". ")
                            + "Saldo: " + reais(saldo) + " | terminais conectados nesta conta: " + abertos);
                    System.out.println("    " + quem + " entrou na conta " + conta.numero);
                } else {
                    enviar(out, "ERRO faça login com: LOGIN <número da conta>");
                }
            }

            // 2) operações
            while (conta != null && (linha = in.readLine()) != null) {
                String[] p = linha.trim().split("\\s+");
                String cmd = p[0].toUpperCase();
                switch (cmd) {
                    case "DEPOSITO", "SAQUE" -> {
                        if (p.length != 2) {
                            enviar(out, "ERRO uso: " + cmd + " <valor>");
                            break;
                        }
                        long valor = centavos(p[1]);
                        if (valor <= 0) {
                            enviar(out, "ERRO valor inválido: " + p[1]);
                            break;
                        }
                        operar(conta, out, cmd, valor);
                    }
                    case "SALDO" -> {
                        long saldo;
                        synchronized (conta) {
                            saldo = conta.saldoCentavos;
                        }
                        enviar(out, "OK Saldo da conta " + conta.numero + ": " + reais(saldo));
                    }
                    case "SAIR" -> {
                        enviar(out, "OK Até logo!");
                        return;
                    }
                    default -> enviar(out, "ERRO comando desconhecido: " + cmd);
                }
            }
        } catch (IOException e) {
            System.out.println("    Erro com " + quem + ": " + e.getMessage());
        } finally {
            if (conta != null && out != null) {
                conta.terminais.remove(out);
            }
            System.out.println("[-] Terminal desconectado: " + quem);
        }
    }

    /** Depósito ou saque: tudo dentro do lock da conta, inclusive o aviso aos outros terminais. */
    static void operar(Conta conta, PrintWriter quemFez, String tipo, long valor) {
        synchronized (conta) {
            if (tipo.equals("SAQUE") && valor > conta.saldoCentavos) {
                enviar(quemFez, "ERRO Saldo insuficiente. Saldo: " + reais(conta.saldoCentavos));
                return;
            }
            conta.saldoCentavos += tipo.equals("DEPOSITO") ? valor : -valor;
            String nome = tipo.equals("DEPOSITO") ? "Depósito" : "Saque";
            String saldo = reais(conta.saldoCentavos);
            enviar(quemFez, "OK " + nome + " de " + reais(valor) + " realizado. Saldo: " + saldo);
            for (PrintWriter outro : conta.terminais) {
                if (outro != quemFez) {
                    enviar(outro, "AVISO " + nome + " de " + reais(valor) + " feito em outro terminal na conta "
                            + conta.numero + ". Saldo atual: " + saldo);
                }
            }
            System.out.println("    Conta " + conta.numero + ": " + nome.toLowerCase() + " de " + reais(valor)
                    + " -> saldo " + saldo);
        }
    }

    static void enviar(PrintWriter out, String msg) {
        synchronized (out) { // várias threads podem escrever no mesmo terminal (avisos)
            out.println(msg);
        }
    }

    /** "50", "50.25" ou "50,25" -> centavos. Retorna -1 se inválido. */
    static long centavos(String s) {
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(s.replace(',', '.'));
            return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    static String reais(long centavos) {
        String sinal = centavos < 0 ? "-" : "";
        long abs = Math.abs(centavos);
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%sR$ %,d,%02d", sinal, abs / 100, abs % 100);
    }
}
