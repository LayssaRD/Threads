package Exercicio04;

import java.text.DecimalFormat;

public class Roletas {

    static class ContadorCentral {
        protected int numPessoas = 0;

        protected synchronized void somar(int n) {
            numPessoas = numPessoas + n;
        }
    }

    static class Roleta implements Runnable {
        public int totPessoas = 0;
        private final int incr;
        private final int giros;
        private final ContadorCentral contadorCentral;

        Roleta(int incr, int giros, ContadorCentral contadorCentral) {
            this.incr = incr;
            this.giros = giros;
            this.contadorCentral = contadorCentral;
        }

        @Override
        public void run() {
            for (int i = 0; i < giros; i++) {
                totPessoas = totPessoas + incr;
                contadorCentral.somar(incr);
            }
        }
    }

    public static void main(String[] args) {
        int qtdRoletas = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int giros = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000;

        ContadorCentral contador = new ContadorCentral();
        Roleta[] roletas = new Roleta[qtdRoletas];
        Thread[] threads = new Thread[qtdRoletas];

        long esperado = 0;
        for (int i = 0; i < qtdRoletas; i++) {
            int incr = i + 1;
            roletas[i] = new Roleta(incr, giros, contador);
            threads[i] = new Thread(roletas[i], "Entrada " + (i + 1));
            esperado += (long) incr * giros;
        }

        for (Thread t : threads) {
            t.start();
        }
        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
        } finally {
            DecimalFormat estilo = new DecimalFormat("###,###,###,###");
            System.out.println("\n  FIM DA CONTAGEM");
            long somaLocal = 0;
            for (int i = 0; i < qtdRoletas; i++) {
                System.out.println("    Entrada " + (i + 1) + ": " + estilo.format(roletas[i].totPessoas) + " pessoas");
                somaLocal += roletas[i].totPessoas;
            }
            System.out.println("        Total (soma das roletas): " + estilo.format(somaLocal));
            System.out.println("        Total CENTRALIZADO:       " + estilo.format(contador.numPessoas));
            System.out.println("        Total ESPERADO:           " + estilo.format(esperado));
            System.out.println(contador.numPessoas == esperado ? "      OK: contagem correta!" : "      ERRO: houve perda de atualizações!");
        }
    }
}