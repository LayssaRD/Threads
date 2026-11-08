package Exercicio04;

import java.util.Random;

/**
 * Problema: uma PADARIA.
 * 
 * Os padeiros (produtores) colocam pães no balcão,
 * que comporta no máximo uma CAPACIDADE de pães.
 * Os clientes (consumidores) retiram od pães.
 *   - balcão cheio  => padeiro espera
 *   - balcão vazio  => cliente espera
 */

public class ProdutorConsumidor {

    static final int CAPACIDADE = 3;
    static final int PRODUTORES = 2;
    static final int CONSUMIDORES = 2;
    static final int PAES_POR_PRODUTOR = 6;

    interface Balcao {
        void colocar(String padeiro, int pao) throws InterruptedException;
        int retirar(String cliente) throws InterruptedException;
    }

    static class Semaforo {
        private int permissoes;

        Semaforo(int permissoes) {
            this.permissoes = permissoes;
        }

        public synchronized void acquire() throws InterruptedException {
            while (permissoes == 0) {
                wait();
            }
            permissoes--;
        }

        public synchronized void release() {
            permissoes++;
            notifyAll();
        }
    }

    // SOLUÇÃO 1: SEMÁFOROS
    //   vagas  = quantidade de espaços livres (começa com CAPACIDADE)
    //   itens  = quantos pães existem no balcão (começa com 0)
    //   mutex  = exclusão mútua no acesso ao vetor (começa com 1)

    static class BalcaoSemaforo implements Balcao {
        private final int[] paes = new int[CAPACIDADE];
        private int entrada = 0;
        private int saida = 0;
        private int quantidade = 0;
        private final Semaforo vagas = new Semaforo(CAPACIDADE);
        private final Semaforo itens = new Semaforo(0);
        private final Semaforo mutex = new Semaforo(1);

        @Override
        public void colocar(String padeiro, int pao) throws InterruptedException {
            vagas.acquire();
            mutex.acquire();
            paes[entrada] = pao;
            entrada = (entrada + 1) % CAPACIDADE;
            quantidade++;
            System.out.println(padeiro + " colocou o pão " + pao + "        | balcão: " + quantidade + "/" + CAPACIDADE);
            mutex.release();
            itens.release();
        }

        @Override
        public int retirar(String cliente) throws InterruptedException {
            itens.acquire();
            mutex.acquire();
            int pao = paes[saida];
            saida = (saida + 1) % CAPACIDADE;
            quantidade--;
            System.out.println(cliente + " retirou o pão " + pao + "        | balcão: " + quantidade + "/" + CAPACIDADE);
            mutex.release();
            vagas.release();
            return pao;
        }
    }

    // SOLUÇÃO 2: MONITOR
    //   Os métodos são synchronized (exclusão mútua) e usamos
    //   wait()/notifyAll() no lugar dos semáforos vagas/itens
    static class BalcaoMonitor implements Balcao {
        private final int[] paes = new int[CAPACIDADE];
        private int entrada = 0;
        private int saida = 0;
        private int quantidade = 0;

        @Override
        public synchronized void colocar(String padeiro, int pao) throws InterruptedException {
            while (quantidade == CAPACIDADE) {
                wait();
            }
            paes[entrada] = pao;
            entrada = (entrada + 1) % CAPACIDADE;
            quantidade++;
            System.out.println(padeiro + " colocou o pão " + pao + "       | balcão: " + quantidade + "/" + CAPACIDADE);
            notifyAll();
        }

        @Override
        public synchronized int retirar(String cliente) throws InterruptedException {
            while (quantidade == 0) {
                wait();
            }
            int pao = paes[saida];
            saida = (saida + 1) % CAPACIDADE;
            quantidade--;
            System.out.println(cliente + " retirou o pão " + pao + "        | balcão: " + quantidade + "/" + CAPACIDADE);
            notifyAll();
            return pao;
        }
    }

    static class Produtor implements Runnable {
        private final String nome;
        private final int base;
        private final Balcao balcao;
        private final Random random = new Random();

        Produtor(String nome, int base, Balcao balcao) {
            this.nome = nome;
            this.base = base;
            this.balcao = balcao;
        }

        @Override
        public void run() {
            try {
                for (int i = 1; i <= PAES_POR_PRODUTOR; i++) {
                    Thread.sleep(random.nextInt(200));
                    balcao.colocar(nome, base + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumidor implements Runnable {
        private final String nome;
        private final Balcao balcao;
        private final Random random = new Random();

        Consumidor(String nome, Balcao balcao) {
            this.nome = nome;
            this.balcao = balcao;
        }

        @Override
        public void run() {
            try {
                int quantidade = PRODUTORES * PAES_POR_PRODUTOR / CONSUMIDORES;
                for (int i = 0; i < quantidade; i++) {
                    balcao.retirar(nome);
                    Thread.sleep(random.nextInt(500));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void simular(String titulo, Balcao balcao) throws InterruptedException {
        System.out.println("\n      " + titulo);
        Thread[] threads = new Thread[PRODUTORES + CONSUMIDORES];
        int t = 0;
        for (int i = 0; i < PRODUTORES; i++) {
            threads[t] = new Thread(new Produtor("Padeiro " + (i + 1), i * 100, balcao));
            t++;
        }
        for (int i = 0; i < CONSUMIDORES; i++) {
            threads[t] = new Thread(new Consumidor("Cliente " + (i + 1), balcao));
            t++;
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }
        System.out.println("        fim: " + titulo);
    }

    public static void main(String[] args) throws InterruptedException {
        simular("PRODUTOR/CONSUMIDOR COM SEMÁFOROS", new BalcaoSemaforo());
        simular("PRODUTOR/CONSUMIDOR COM MONITOR", new BalcaoMonitor());
    }
}
