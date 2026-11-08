package Exercicio02;

import java.util.Random;

public class BarbeiroDorminhoco {

    private final int cadeiras;
    private final int[] fila;
    private int inicio = 0;
    private int ocupadas = 0;
    private final boolean[] cortados;
    private boolean barbeiroDormindo = false;
    private boolean aberta = true;

    public BarbeiroDorminhoco(int cadeiras, int totalClientes) {
        this.cadeiras = cadeiras;
        this.fila = new int[cadeiras];
        this.cortados = new boolean[totalClientes + 1];
    }

    // thread do CLIENTE
    public synchronized boolean chegar(int id) {
        if (ocupadas >= cadeiras) {
            log("Cliente " + id + " encontrou a barbearia CHEIA e foi embora.");
            return false;
        }
        fila[(inicio + ocupadas) % cadeiras] = id;
        ocupadas++;
        log("Cliente " + id + " sentou (cadeiras ocupadas: " + ocupadas + "/" + cadeiras + ")");
        if (barbeiroDormindo) {
            barbeiroDormindo = false;
            log("Cliente " + id + " ACORDOU o barbeiro.");
        }
        notifyAll();

        while (!cortados[id]) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log("Cliente " + id + " saiu com o cabelo cortado.");
        return true;
    }

    // thread do BARBEIRO
    public synchronized int proximoCliente() {
        while (ocupadas == 0) {
            if (!aberta) {
                return -1;
            }
            barbeiroDormindo = true;
            log("Barbeiro: não há clientes... vai DORMIR (zzz)");
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        barbeiroDormindo = false;
        int id = fila[inicio];
        inicio = (inicio + 1) % cadeiras;
        ocupadas--;
        log("Barbeiro chamou o Cliente " + id + " (cadeiras ocupadas: " + ocupadas + "/" + cadeiras + ")");
        return id;
    }

    public synchronized void terminarCorte(int id) {
        cortados[id] = true;
        notifyAll();
    }

    public synchronized void fechar() {
        aberta = false;
        notifyAll();
    }

    private static synchronized void log(String texto) {
        System.out.println(texto);
    }

    static class Barbeiro implements Runnable {
        private final BarbeiroDorminhoco barbearia;

        Barbeiro(BarbeiroDorminhoco barbearia) {
            this.barbearia = barbearia;
        }

        @Override
        public void run() {
            while (true) {
                int id = barbearia.proximoCliente();
                if (id < 0) {
                    break;
                }
                log("Barbeiro CORTANDO o cabelo do Cliente " + id + "...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                barbearia.terminarCorte(id);
            }
            log("Barbeiro foi para casa.");
        }
    }

    static class Cliente implements Runnable {
        private final int id;
        private final BarbeiroDorminhoco barbearia;

        Cliente(int id, BarbeiroDorminhoco barbearia) {
            this.id = id;
            this.barbearia = barbearia;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(new Random().nextInt(5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log("Cliente " + id + " chegou à barbearia.");
            barbearia.chegar(id);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int clientes = 10, cadeiras = 3;
        if (args.length >= 2) {
            clientes = Integer.parseInt(args[0]);
            cadeiras = Integer.parseInt(args[1]);
        }
        System.out.println("Barbearia aberta: " + clientes + " clientes, " + cadeiras + " cadeiras de espera\n");

        BarbeiroDorminhoco barbearia = new BarbeiroDorminhoco(cadeiras, clientes);
        Thread barbeiro = new Thread(new Barbeiro(barbearia), "Barbeiro");
        barbeiro.start();

        Thread[] threadsClientes = new Thread[clientes];
        for (int i = 0; i < clientes; i++) {
            threadsClientes[i] = new Thread(new Cliente(i + 1, barbearia), "Cliente " + (i + 1));
            threadsClientes[i].start();
        }
        for (int i = 0; i < clientes; i++) {
            threadsClientes[i].join();
        }
        barbearia.fechar();
        barbeiro.join();
        System.out.println("\nPrograma finalizado.");
    }
}
