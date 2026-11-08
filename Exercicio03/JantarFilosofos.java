package Exercicio03;

import java.util.Random;

public class JantarFilosofos {

    static final int N = 5;

    static class Palito {
        final int id;

        Palito(int id) {
            this.id = id;
        }
    }

    static class Filosofo implements Runnable {
        private final int id;
        private final Palito direita;
        private final Palito esquerda;
        private final int refeicoes;
        private final Random random = new Random();

        Filosofo(int id, Palito direita, Palito esquerda, int refeicoes) {
            this.id = id;
            this.direita = direita;
            this.esquerda = esquerda;
            this.refeicoes = refeicoes;
        }

        @Override
        public void run() {
            Palito primeiro = direita.id < esquerda.id ? direita : esquerda;
            Palito segundo = direita.id < esquerda.id ? esquerda : direita;

            for (int i = 1; i <= refeicoes; i++) {
                meditar();
                System.out.println("Filósofo " + id + " está com FOME (refeição " + i + ")");

                synchronized (primeiro) {
                    System.out.println("Filósofo " + id + " pegou o palito " + primeiro.id);
                    synchronized (segundo) {
                        System.out.println("Filósofo " + id + " pegou o palito " + segundo.id
                                + " e está COMENDO (palitos " + direita.id + " e " + esquerda.id + ")");
                        dormir(500 + random.nextInt(1000));
                        System.out.println("Filósofo " + id + " terminou de comer e soltou os palitos "
                                + primeiro.id + " e " + segundo.id);
                    }
                }
            }
            System.out.println("Filósofo " + id + " terminou o jantar.");
        }

        private void meditar() {
            System.out.println("Filósofo " + id + " está MEDITANDO");
            dormir(300 + random.nextInt(1000));
        }

        private void dormir(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int refeicoes = args.length > 0 ? Integer.parseInt(args[0]) : 3;

        Palito[] palitos = new Palito[N];
        for (int i = 0; i < N; i++) {
            palitos[i] = new Palito(i);
        }

        Thread[] filosofos = new Thread[N];
        for (int i = 0; i < N; i++) {
            Palito direita = palitos[i];
            Palito esquerda = palitos[(i + 1) % N];
            filosofos[i] = new Thread(new Filosofo(i, direita, esquerda, refeicoes), "Filósofo " + i);
            filosofos[i].start();
        }
        for (Thread t : filosofos) {
            t.join();
        }
        System.out.println("\nTodos os filósofos meditaram e comeram. Sem deadlock!");
    }
}