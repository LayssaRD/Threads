package Exercicio04;

import java.util.Random;

public class Banco {

    static class Conta {
        final int id;
        final String nome;
        private double saldo;

        Conta(int id, String nome, double saldoInicial) {
            this.id = id;
            this.nome = nome;
            this.saldo = saldoInicial;
        }

        synchronized double depositar(double valor) {
            saldo += valor;
            return saldo;
        }

        synchronized double sacar(double valor) {
            if (saldo < valor) {
                return -1;
            }
            saldo -= valor;
            return saldo;
        }

        synchronized double creditarJuros(double percentual) {
            saldo += saldo * percentual / 100.0;
            return saldo;
        }

        synchronized double getSaldo() {
            return saldo;
        }
    }

    static boolean transferir(Conta origem, Conta destino, double valor) {
        Conta primeira = origem.id < destino.id ? origem : destino;
        Conta segunda = origem.id < destino.id ? destino : origem;
        synchronized (primeira) {
            synchronized (segunda) {
                if (origem.sacar(valor) < 0) {
                    return false;
                }
                destino.depositar(valor);
                return true;
            }
        }
    }

    static synchronized void log(String s) {
        System.out.println(s);
    }

    static void pausaAleatoria() {
        try {
            Thread.sleep(new Random().nextInt(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class Deposito extends Thread {
        private final Conta c;
        private final double valor;

        Deposito(Conta c, double valor) {
            this.c = c;
            this.valor = valor;
        }

        public void run() {
            pausaAleatoria();
            double novo = c.depositar(valor);
            log(String.format("DEPÓSITO R$ %.2f na %s -> saldo %s = %.2f", valor, c.nome, c.nome, novo));
        }
    }

    static class Saque extends Thread {
        private final Conta c;
        private final double valor;

        Saque(Conta c, double valor) {
            this.c = c;
            this.valor = valor;
        }

        public void run() {
            pausaAleatoria();
            double novo = c.sacar(valor);
            if (novo >= 0) {
                log(String.format("SAQUE R$ %.2f da %s -> saldo %s = %.2f", valor, c.nome, c.nome, novo));
            } else {
                log(String.format("SAQUE R$ %.2f da %s RECUSADO (saldo insuficiente)", valor, c.nome));
            }
        }
    }

    static class CreditoJuros extends Thread {
        private final Conta c;
        private final double percentual;

        CreditoJuros(Conta c, double percentual) {
            this.c = c;
            this.percentual = percentual;
        }

        public void run() {
            pausaAleatoria();
            double novo = c.creditarJuros(percentual);
            log(String.format("JUROS %.1f%% na %s -> saldo %s = %.2f", percentual, c.nome, c.nome, novo));
        }
    }

    static class Transferencia extends Thread {
        private final Conta origem, destino;
        private final double valor;

        Transferencia(Conta origem, Conta destino, double valor) {
            this.origem = origem;
            this.destino = destino;
            this.valor = valor;
        }

        public void run() {
            pausaAleatoria();
            if (transferir(origem, destino, valor)) {
                log(String.format("TRANSFERÊNCIA R$ %.2f %s -> %s", valor, origem.nome, destino.nome));
            } else {
                log(String.format("TRANSFERÊNCIA R$ %.2f %s -> %s RECUSADA (saldo insuficiente)", valor, origem.nome, destino.nome));
            }
        }
    }

    static void executar(String titulo, Conta a, Conta b, Thread... acoes) throws InterruptedException {
        System.out.println("\n      " + titulo);
        System.out.printf("Saldos iniciais: A = %.2f | B = %.2f%n", a.getSaldo(), b.getSaldo());
        for (Thread t : acoes) {
            t.start();
        }
        for (Thread t : acoes) {
            t.join();
        }
        System.out.printf("Saldos finais:   A = %.2f | B = %.2f | TOTAL = %.2f%n",
                a.getSaldo(), b.getSaldo(), a.getSaldo() + b.getSaldo());
    }

    public static void main(String[] args) throws InterruptedException {
        // i: Transferência, Depósito, Crédito de juros
        Conta a1 = new Conta(1, "Conta A", 1000);
        Conta b1 = new Conta(2, "Conta B", 500);
        executar("CENÁRIO i: Transferência, Depósito, Juros", a1, b1,
                new Transferencia(a1, b1, 400),
                new Deposito(a1, 200),
                new CreditoJuros(a1, 10));

        // ii: Saque, Depósito, Crédito de juros, Transferência
        Conta a2 = new Conta(1, "Conta A", 1000);
        Conta b2 = new Conta(2, "Conta B", 500);
        executar("CENÁRIO ii: Saque, Depósito, Juros, Transferência", a2, b2,
                new Saque(a2, 300),
                new Deposito(a2, 200),
                new CreditoJuros(a2, 10),
                new Transferencia(a2, b2, 600));
    }
}
