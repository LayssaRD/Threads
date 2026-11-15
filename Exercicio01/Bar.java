package Exercicio01;

import java.util.Arrays;
import java.util.Random;

/**
 * Uma rodada global permite que cada cliente decida uma vez entre pedir e pular.
 * Cada garcom atende os interessados em lotes de, no maximo, C pedidos.
 * Uma rodada pode ter varios lotes. As filas guardam solicitacoes aguardando;
 * o garcom so assume outro lote depois de entregar completamente o anterior.
 *
 * Uso: java Exercicio01.Bar [clientes garcons capacidade rodadas]
 */
public class Bar {

    static class Log {
        private static final long INICIO = System.currentTimeMillis();

        static synchronized void msg(String texto) {
            System.out.printf("[%5d ms] %s%n", System.currentTimeMillis() - INICIO, texto);
        }
    }

    static class Pedido {
        final int clienteId;
        final int garcomId;
        final int rodada;
        // Estes campos sao acessados somente sob o monitor de MonitorBar.
        boolean emAtendimento;
        boolean entregue;

        Pedido(int clienteId, int garcomId, int rodada) {
            this.clienteId = clienteId;
            this.garcomId = garcomId;
            this.rodada = rodada;
        }
    }

    /** Um unico objeto compartilhado: somente um preparo ocorre por vez. */
    static class Bartender {
        public synchronized void prepararPedidos(int garcomId, int rodada, int quantidade)
                throws InterruptedException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            Log.msg("BARTENDER: rodada " + rodada + ", preparando " + quantidade
                    + " pedido(s) do Garcom " + garcomId);
            Thread.sleep(400L * quantidade);
            Log.msg("BARTENDER: pedidos do Garcom " + garcomId + " prontos.");
        }
    }

    /**
     * Monitor unico para estado das rodadas, filas, decisoes e entregas.
     * Nao ha sleep, preparo ou consumo dentro de seus metodos sincronizados.
     */
    static class MonitorBar {
        private final int clientes;
        private final int garcons;
        private final int capacidade;
        private final int rodadasTotal;
        private final int[] clientesPorGarcom;
        private final Pedido[][] filas;
        private final int[] inicio;
        private final int[] tamanho;
        private final int[] decisoesPendentes;
        private final int[] entregasPendentes;
        private final int[] ultimaDecisao;
        private final boolean[] atendendo;
        private final boolean[] concluiuRodada;
        private int rodadaAtual = 1;
        private int garconsConcluidos;
        private boolean encerrado;
        private boolean cancelado;
        private Thread[] participantes = new Thread[0];

        MonitorBar(int clientes, int garcons, int capacidade, int rodadas) {
            if (clientes < 1 || garcons < 1 || capacidade < 1 || rodadas < 1) {
                throw new IllegalArgumentException("Todos os parametros devem ser >= 1.");
            }
            this.clientes = clientes;
            this.garcons = garcons;
            this.capacidade = capacidade;
            this.rodadasTotal = rodadas;
            clientesPorGarcom = new int[garcons];
            for (int c = 0; c < clientes; c++) {
                clientesPorGarcom[c % garcons]++;
            }
            filas = new Pedido[garcons][];
            for (int g = 0; g < garcons; g++) {
                // Uma solicitacao por cliente por rodada; lote tem limite separado C.
                filas[g] = new Pedido[clientesPorGarcom[g]];
            }
            inicio = new int[garcons];
            tamanho = new int[garcons];
            decisoesPendentes = new int[garcons];
            entregasPendentes = new int[garcons];
            ultimaDecisao = new int[clientes + 1];
            atendendo = new boolean[garcons];
            concluiuRodada = new boolean[garcons];
            prepararRodada();
        }

        private void prepararRodada() {
            // Chamado no construtor ou mantendo este monitor; publica tudo de uma vez.
            System.arraycopy(clientesPorGarcom, 0, decisoesPendentes, 0, garcons);
            Arrays.fill(inicio, 0);
            Arrays.fill(concluiuRodada, false);
            garconsConcluidos = 0;
            Log.msg("=== RODADA GLOBAL " + rodadaAtual + "/" + rodadasTotal + " ===");
        }

        synchronized void registrarThreads(Thread[] threads) {
            // A main registra todas antes de iniciar qualquer uma.
            participantes = threads.clone();
        }

        synchronized int aguardarRodada(int anterior) throws InterruptedException {
            while (!encerrado && rodadaAtual <= anterior) {
                wait();
            }
            return encerrado ? -1 : rodadaAtual;
        }

        synchronized Pedido registrarDecisao(int clienteId, int rodada, boolean querPedir) {
            if (clienteId < 1 || clienteId > clientes) {
                throw new IllegalArgumentException("ID de cliente invalido.");
            }
            if (encerrado) {
                return null;
            }
            if (rodada != rodadaAtual || ultimaDecisao[clienteId] == rodada) {
                throw new IllegalStateException("Cada cliente decide uma vez na rodada atual.");
            }
            int g = (clienteId - 1) % garcons;
            ultimaDecisao[clienteId] = rodada;
            decisoesPendentes[g]--;
            Pedido pedido = null;
            if (querPedir) {
                pedido = new Pedido(clienteId, g + 1, rodada);
                int posicao = (inicio[g] + tamanho[g]) % filas[g].length;
                filas[g][posicao] = pedido;
                tamanho[g]++;
                Log.msg("Cliente " + clienteId + " solicitou pedido na rodada " + rodada
                        + "; fila de espera do Garcom " + (g + 1) + ", posicao " + tamanho[g]);
            } else {
                Log.msg("Cliente " + clienteId + " decidiu PULAR a rodada " + rodada);
            }
            notifyAll();
            return pedido;
        }

        synchronized Pedido[] recebeMaximoPedidos(int garcomId, int rodada)
                throws InterruptedException {
            int g = indiceGarcom(garcomId);
            // Ainda pode chegar algum pedido? Aguarda C; depois da ultima decisao,
            // libera tambem um lote parcial, ou retorna null se nao houver pedidos.
            while (!encerrado && tamanho[g] < capacidade && decisoesPendentes[g] > 0) {
                wait();
            }
            if (encerrado) {
                return null;
            }
            if (rodada != rodadaAtual || atendendo[g] || concluiuRodada[g]) {
                throw new IllegalStateException("Estado invalido para iniciar um lote.");
            }
            if (tamanho[g] == 0) {
                return null;
            }
            int quantidade = Math.min(capacidade, tamanho[g]);
            Pedido[] lote = new Pedido[quantidade];
            StringBuilder ordem = new StringBuilder();
            for (int i = 0; i < quantidade; i++) {
                Pedido p = filas[g][inicio[g]];
                filas[g][inicio[g]] = null;
                inicio[g] = (inicio[g] + 1) % filas[g].length;
                tamanho[g]--;
                p.emAtendimento = true;
                lote[i] = p;
                if (i > 0) {
                    ordem.append(", ");
                }
                ordem.append("C").append(p.clienteId);
            }
            atendendo[g] = true;
            entregasPendentes[g] = quantidade;
            Log.msg("Garcom " + garcomId + " assumiu lote de " + quantidade + "/"
                    + capacidade + " na rodada " + rodada + "; ordem FIFO: " + ordem);
            return lote;
        }

        synchronized boolean entregarPedido(Pedido pedido) {
            if (cancelado) {
                return false;
            }
            if (!pedido.emAtendimento || pedido.entregue) {
                throw new IllegalStateException("Pedido nao esta aguardando entrega no lote.");
            }
            pedido.entregue = true;
            pedido.emAtendimento = false;
            entregasPendentes[pedido.garcomId - 1]--;
            Log.msg("Garcom " + pedido.garcomId + " ENTREGOU ao Cliente " + pedido.clienteId
                    + " (rodada " + pedido.rodada + ")");
            notifyAll();
            return true;
        }

        synchronized void finalizarLote(int garcomId) {
            int g = indiceGarcom(garcomId);
            if (cancelado) {
                return;
            }
            if (!atendendo[g] || entregasPendentes[g] != 0) {
                throw new IllegalStateException("O lote precisa ser totalmente entregue.");
            }
            atendendo[g] = false;
            Log.msg("Garcom " + garcomId + " concluiu todas as entregas do lote.");
            notifyAll();
        }

        synchronized boolean esperaPedido(Pedido pedido) throws InterruptedException {
            while (!pedido.entregue && !cancelado) {
                wait();
            }
            // Cancelar nunca transforma um pedido pendente em entregue.
            return pedido.entregue;
        }

        synchronized void concluirRodada(int garcomId, int rodada) {
            int g = indiceGarcom(garcomId);
            if (encerrado) {
                return;
            }
            if (rodada != rodadaAtual || concluiuRodada[g] || atendendo[g]
                    || tamanho[g] != 0 || decisoesPendentes[g] != 0) {
                throw new IllegalStateException("Ainda existem decisoes ou pedidos nesta rodada.");
            }
            concluiuRodada[g] = true;
            garconsConcluidos++;
            Log.msg("Garcom " + garcomId + " concluiu a rodada " + rodada);
            if (garconsConcluidos == garcons) {
                if (rodadaAtual == rodadasTotal) {
                    encerrado = true;
                    Log.msg("BAR FECHADO: todas as rodadas e entregas foram concluidas.");
                } else {
                    rodadaAtual++;
                    prepararRodada();
                }
                notifyAll();
            }
        }

        synchronized boolean isCancelado() {
            return cancelado;
        }

        void cancelar(String motivo) {
            Thread[] threads;
            synchronized (this) {
                if (cancelado) {
                    return;
                }
                cancelado = true;
                encerrado = true;
                threads = participantes.clone();
                Log.msg("BAR CANCELADO: " + motivo);
                notifyAll();
            }
            // Interrompe tambem quem esta dormindo no preparo, na entrega ou consumo.
            // Executado fora deste monitor para nao criar inversao de locks.
            for (Thread t : threads) {
                if (t != Thread.currentThread()) {
                    t.interrupt();
                }
            }
        }

        private int indiceGarcom(int id) {
            if (id < 1 || id > garcons) {
                throw new IllegalArgumentException("ID de garcom invalido.");
            }
            return id - 1;
        }
    }

    static class Garcom implements Runnable {
        private final int id;
        private final MonitorBar bar;
        private final Bartender bartender;

        Garcom(int id, MonitorBar bar, Bartender bartender) {
            this.id = id;
            this.bar = bar;
            this.bartender = bartender;
        }

        private void registraPedidos(Pedido[] lote) throws InterruptedException {
            Log.msg("Garcom " + id + " indo a COPA (rodada " + lote[0].rodada + ")");
            bartender.prepararPedidos(id, lote[0].rodada, lote.length);
        }

        private boolean entregaPedidos(Pedido[] lote) throws InterruptedException {
            for (Pedido p : lote) {
                Thread.sleep(150);
                if (!bar.entregarPedido(p)) {
                    return false;
                }
            }
            bar.finalizarLote(id);
            return !bar.isCancelado();
        }

        @Override
        public void run() {
            int anterior = 0;
            try {
                int rodada;
                while ((rodada = bar.aguardarRodada(anterior)) != -1) {
                    anterior = rodada;
                    Pedido[] lote;
                    while ((lote = bar.recebeMaximoPedidos(id, rodada)) != null) {
                        if (bar.isCancelado()) {
                            return;
                        }
                        registraPedidos(lote);
                        if (!entregaPedidos(lote)) {
                            return;
                        }
                    }
                    bar.concluirRodada(id, rodada);
                }
            } catch (InterruptedException e) {
                bar.cancelar("Garcom " + id + " interrompido.");
                Thread.currentThread().interrupt();
            } finally {
                Log.msg("Garcom " + id + " encerrou o expediente.");
            }
        }
    }

    static class Cliente implements Runnable {
        private final int id;
        private final MonitorBar bar;
        private final Random random = new Random();

        Cliente(int id, MonitorBar bar) {
            this.id = id;
            this.bar = bar;
        }

        private void recebePedido(int rodada) {
            Log.msg("Cliente " + id + " RECEBEU seu pedido da rodada " + rodada);
        }

        private void consomePedido() throws InterruptedException {
            int tempo = 500 + random.nextInt(1500);
            Log.msg("Cliente " + id + " consumindo por " + tempo + " ms");
            Thread.sleep(tempo);
        }

        @Override
        public void run() {
            int anterior = 0;
            try {
                int rodada;
                while ((rodada = bar.aguardarRodada(anterior)) != -1) {
                    anterior = rodada;
                    // Sorteio unico por rodada: quem pula nao tenta de novo nela.
                    Pedido pedido = bar.registrarDecisao(id, rodada, random.nextInt(100) < 70);
                    if (pedido != null) {
                        if (!bar.esperaPedido(pedido) || bar.isCancelado()) {
                            break;
                        }
                        recebePedido(rodada);
                        consomePedido();
                    }
                }
            } catch (InterruptedException e) {
                bar.cancelar("Cliente " + id + " interrompido.");
                Thread.currentThread().interrupt();
            } finally {
                Log.msg("Cliente " + id + " foi embora.");
            }
        }
    }

    private static int[] lerParametros(String[] args) {
        if (args.length != 0 && args.length != 4) {
            throw new IllegalArgumentException("Informe nenhum argumento ou exatamente quatro.");
        }
        int[] valores = {8, 2, 3, 3};
        if (args.length == 4) {
            try {
                for (int i = 0; i < 4; i++) {
                    valores[i] = Integer.parseInt(args[i]);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Os quatro parametros devem ser inteiros validos.");
            }
        }
        for (int valor : valores) {
            if (valor < 1) {
                throw new IllegalArgumentException("Todos os parametros devem ser >= 1.");
            }
        }
        return valores;
    }

    public static void main(String[] args) {
        int[] p;
        try {
            p = lerParametros(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.err.println("Uso: java Exercicio01.Bar [clientes garcons capacidade rodadas]");
            return;
        }
        int clientes = p[0], garcons = p[1], capacidade = p[2], rodadas = p[3];
        System.out.printf("BAR ABERTO: %d clientes, %d garcons, capacidade %d, %d rodadas globais%n",
                clientes, garcons, capacidade, rodadas);
        MonitorBar bar = new MonitorBar(clientes, garcons, capacidade, rodadas);
        Bartender bartender = new Bartender();
        Thread[] threads = new Thread[garcons + clientes];
        for (int g = 0; g < garcons; g++) {
            threads[g] = new Thread(new Garcom(g + 1, bar, bartender), "Garcom " + (g + 1));
        }
        for (int c = 0; c < clientes; c++) {
            threads[garcons + c] = new Thread(new Cliente(c + 1, bar), "Cliente " + (c + 1));
            Log.msg("Cliente " + (c + 1) + " vinculado ao Garcom " + ((c % garcons) + 1));
        }
        bar.registrarThreads(threads);
        for (Thread t : threads) {
            t.setUncaughtExceptionHandler((thread, erro) -> {
                bar.cancelar("Falha na thread " + thread.getName() + ": " + erro);
                erro.printStackTrace();
            });
            t.start();
        }
        boolean interrompida = false;
        for (Thread t : threads) {
            boolean terminou = false;
            while (!terminou) {
                try {
                    t.join();
                    terminou = true;
                } catch (InterruptedException e) {
                    interrompida = true;
                    bar.cancelar("Thread principal interrompida.");
                }
            }
        }
        System.out.println(bar.isCancelado() ? "\nPrograma cancelado." : "\nPrograma finalizado.");
        if (interrompida) {
            Thread.currentThread().interrupt();
        }
    }
}
