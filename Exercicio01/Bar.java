package Exercicio01;

import java.util.Random;

public class Bar {

    static int garconsAtivos;

    static synchronized void garcomTerminou() {
        garconsAtivos--;
        if (garconsAtivos == 0) {
            Log.msg("BAR FECHADO");
        }
    }

    static class Log {
        private static final long inicio = System.currentTimeMillis();

        static synchronized void msg(String texto) {
            long t = System.currentTimeMillis() - inicio;
            System.out.printf("[%5d ms] %s%n", t, texto);
        }
    }

    // um pedido feito por um cliente
    static class Pedido {
        final int clienteId;
        boolean entregue = false;

        Pedido(int clienteId) {
            this.clienteId = clienteId;
        }
    }

    // Bartender só atende um garçom por vez
    static class Bartender {
        public synchronized void prepararPedidos(int garcomId, int qtd) {
            Log.msg("  >> BARTENDER recebeu " + qtd + " pedido(s) do Garçom " + garcomId
                    + " e está preparando..");
            try {
                Thread.sleep(400L * qtd);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Log.msg("  >> BARTENDER terminou os pedidos do Garçom " + garcomId);
        }
    }

    // THREAD do garçom que monitora a fila de pedidos
    static class Garcom implements Runnable {
        private final int id;
        private final int capacidade;
        private final int totalClientes;
        private final int rodadasTotal;
        private final Bartender bartender;

        private final Pedido[] fila;
        private int tamanho = 0;
        private boolean atendendo = false;
        private boolean encerrado = false;
        private int proximaSenha = 0;
        private int senhaDaVez = 0;
        private int rodada = 0;

        Garcom(int id, int capacidade, int totalClientes, int rodadasTotal, Bartender bartender) {
            this.id = id;
            this.capacidade = capacidade;
            this.totalClientes = totalClientes;
            this.rodadasTotal = rodadasTotal;
            this.bartender = bartender;
            this.fila = new Pedido[capacidade];
        }

        // métodos dos CLIENTES
        public synchronized boolean fazPedido(Pedido p) {
            int minhaSenha = proximaSenha++;
            Log.msg("Cliente " + p.clienteId + " quer pedir ao Garçom " + id + " (senha " + minhaSenha + ")");
            while (!encerrado && (minhaSenha != senhaDaVez || atendendo || tamanho >= capacidade)) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (encerrado) {
                return false;
            }
            fila[tamanho] = p;
            tamanho++;
            senhaDaVez++;
            Log.msg("Cliente " + p.clienteId + " ENTROU na fila do Garçom " + id
                    + " na posição " + tamanho + "/" + capacidade);
            notifyAll();
            return true;
        }

        public synchronized void esperaPedido(Pedido p) {
            while (!p.entregue) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        public synchronized boolean isEncerrado() {
            return encerrado;
        }

        // chamados pelo GARÇOM
        private synchronized Pedido[] recebeMaximoPedidos(int alvo) {
            Log.msg("Garçom " + id + " aguardando pedidos (0/" + alvo + ")");
            while (tamanho < alvo) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            atendendo = true;
            Pedido[] lote = new Pedido[tamanho];
            for (int i = 0; i < tamanho; i++) {
                lote[i] = fila[i];
            }
            tamanho = 0;
            return lote;
        }

        private void registraPedidos(Pedido[] lote) {
            String ordem = "";
            for (int i = 0; i < lote.length; i++) {
                ordem = ordem + "C" + lote[i].clienteId + " ";
            }
            Log.msg("Garçom " + id + " recebeu " + lote.length + " pedidos, ordem de chegada: "
                    + ordem.trim() + " -> indo à COPA");
            bartender.prepararPedidos(id, lote.length);
        }

        private void entregaPedidos(Pedido[] lote, boolean ultimaRodada) {
            for (int i = 0; i < lote.length; i++) {
                Log.msg("Garçom " + id + " entregando pedido ao Cliente " + lote[i].clienteId);
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (this) {
                    lote[i].entregue = true;
                    notifyAll();
                }
            }
            synchronized (this) {
                atendendo = false;
                if (ultimaRodada) {
                    encerrado = true;
                }
                notifyAll();
            }
            Log.msg("Garçom " + id + " LIBERADO para novos pedidos");
        }

        @Override
        public void run() {
            if (totalClientes == 0) {
                Log.msg("Garçom " + id + " não tem clientes atribuídos.");
                terminar();
                return;
            }
            int alvo = capacidade;
            if (totalClientes < capacidade) {
                alvo = totalClientes;
            }
            while (rodada < rodadasTotal) {
                Log.msg("Garçom " + id + " - RODADA " + (rodada + 1) + "/" + rodadasTotal);
                Pedido[] lote = recebeMaximoPedidos(alvo);
                registraPedidos(lote);
                rodada++;
                entregaPedidos(lote, rodada == rodadasTotal);
            }
            terminar();
        }

        private void terminar() {
            synchronized (this) {
                encerrado = true;
                notifyAll();
            }
            Log.msg("Garçom " + id + " encerrou o expediente.");
            garcomTerminou();
        }
    }

    // Cliente
    static class Cliente implements Runnable {
        private final int id;
        private final Garcom garcom;
        private final Random random = new Random();

        Cliente(int id, Garcom garcom) {
            this.id = id;
            this.garcom = garcom;
        }

        @Override
        public void run() {
            while (!garcom.isEncerrado()) {
                if (random.nextInt(100) < 70) {
                    Pedido p = new Pedido(id);
                    if (!fazPedido(p)) {
                        break;
                    }
                    garcom.esperaPedido(p);
                    recebePedido();
                    consomePedido();
                } else {
                    Log.msg("Cliente " + id + " NÃO quer pedir agora");
                    dormir(300 + random.nextInt(500));
                }
            }
            Log.msg("Cliente " + id + " foi embora");
        }

        private boolean fazPedido(Pedido p) {
            return garcom.fazPedido(p);
        }

        private void recebePedido() {
            Log.msg("Cliente " + id + " RECEBEU o pedido");
        }

        private void consomePedido() {
            int tempo = 500 + random.nextInt(1500);
            Log.msg("Cliente " + id + " consumindo por " + tempo + " ms");
            dormir(tempo);
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
        int clientes = 8, garcons = 2, capacidade = 3, rodadas = 3;
        if (args.length >= 4) {
            clientes = Integer.parseInt(args[0]);
            garcons = Integer.parseInt(args[1]);
            capacidade = Integer.parseInt(args[2]);
            rodadas = Integer.parseInt(args[3]);
        }
        if (clientes < 1 || garcons < 1 || capacidade < 1 || rodadas < 1) {
            System.out.println("Todos os parâmetros devem ser >= 1");
            return;
        }

        System.out.printf("BAR ABERTO: %d clientes, %d garçons, capacidade %d, %d rodadas%n%n",
                clientes, garcons, capacidade, rodadas);

        Bartender bartender = new Bartender();
        garconsAtivos = garcons;

        int[] qtdPorGarcom = new int[garcons];
        for (int i = 0; i < clientes; i++) {
            qtdPorGarcom[i % garcons]++;
        }

        Garcom[] listaGarcons = new Garcom[garcons];
        Thread[] threads = new Thread[garcons + clientes];
        int t = 0;
        for (int g = 0; g < garcons; g++) {
            listaGarcons[g] = new Garcom(g + 1, capacidade, qtdPorGarcom[g], rodadas, bartender);
            threads[t] = new Thread(listaGarcons[g], "Garçom " + (g + 1));
            t++;
        }
        for (int c = 0; c < clientes; c++) {
            threads[t] = new Thread(new Cliente(c + 1, listaGarcons[c % garcons]), "Cliente " + (c + 1));
            t++;
            Log.msg("Cliente " + (c + 1) + " será atendido pelo Garçom " + ((c % garcons) + 1));
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }
        System.out.println("\nPrograma finalizado");
    }
}