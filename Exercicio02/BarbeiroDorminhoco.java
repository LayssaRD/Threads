import java.util.Random;

public class BarbeiroDorminhoco {

    // N conta somente as cadeiras de espera; a cadeira do corte é separada.
    private final int cadeiras;
    private final int[] fila;
    private int inicio = 0;
    private int ocupadas = 0;
    private int clienteEmAtendimento = 0;

    // O cliente de id i usa a posição i - 1. Cada id pode chegar uma única vez.
    private final boolean[] chegaram;
    private final boolean[] cortados;
    private final boolean[] cancelados;
    private boolean barbeiroDormindo = false;
    private boolean aberta = true;
    private boolean abortada = false;

    public BarbeiroDorminhoco(int cadeiras, int totalClientes) {
        if (cadeiras < 1) {
            throw new IllegalArgumentException("A quantidade de cadeiras deve ser >= 1.");
        }
        if (totalClientes < 0) {
            throw new IllegalArgumentException("A quantidade de clientes deve ser >= 0.");
        }
        this.cadeiras = cadeiras;
        this.fila = new int[cadeiras];
        this.chegaram = new boolean[totalClientes];
        this.cortados = new boolean[totalClientes];
        this.cancelados = new boolean[totalClientes];
    }

    private void validarId(int id) {
        if (id < 1 || id > chegaram.length) {
            throw new IllegalArgumentException("Id de cliente fora do intervalo 1.."
                    + chegaram.length + ": " + id);
        }
    }

    // Executado pela thread do cliente. wait() libera o monitor durante a espera.
    public synchronized boolean chegar(int id) {
        validarId(id);
        int indice = id - 1;
        if (chegaram[indice]) {
            throw new IllegalArgumentException("O Cliente " + id + " já chegou anteriormente.");
        }
        chegaram[indice] = true;

        if (!aberta) {
            cancelados[indice] = true;
            log("Cliente " + id + " encontrou a barbearia FECHADA e foi embora.");
            return false;
        }
        if (Thread.currentThread().isInterrupted()) {
            cancelados[indice] = true;
            log("Cliente " + id + " cancelou antes de entrar na fila.");
            return false;
        }
        if (ocupadas >= cadeiras) {
            cancelados[indice] = true;
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

        while (!cortados[indice] && !cancelados[indice]) {
            try {
                wait();
            } catch (InterruptedException e) {
                // O corte pode ter terminado antes de a thread readquirir o monitor.
                if (!cortados[indice]) {
                    cancelarCliente(id);
                }
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (cortados[indice]) {
            log("Cliente " + id + " saiu com o cabelo cortado.");
            return true;
        }
        log("Cliente " + id + " saiu com o atendimento cancelado.");
        return false;
    }

    // Chamado somente enquanto esta thread possui o monitor.
    // Remove da fila sem alterar a ordem dos demais clientes.
    private void cancelarCliente(int id) {
        cancelados[id - 1] = true;
        for (int i = 0; i < ocupadas; i++) {
            if (fila[(inicio + i) % cadeiras] == id) {
                for (int j = i; j < ocupadas - 1; j++) {
                    fila[(inicio + j) % cadeiras] = fila[(inicio + j + 1) % cadeiras];
                }
                fila[(inicio + ocupadas - 1) % cadeiras] = 0;
                ocupadas--;
                log("Cliente " + id + " liberou sua cadeira de espera.");
                break;
            }
        }
        // Se já estiver sendo atendido, o barbeiro liberará a cadeira do corte.
        notifyAll();
    }

    // Executado pela única thread do barbeiro.
    public synchronized int proximoCliente() {
        if (Thread.currentThread().isInterrupted()) {
            abortar("Barbeiro interrompido.");
            return -1;
        }
        while (ocupadas == 0 && aberta) {
            barbeiroDormindo = true;
            log("Barbeiro: não há clientes... vai DORMIR (zzz)");
            try {
                wait();
            } catch (InterruptedException e) {
                abortar("Barbeiro interrompido enquanto aguardava clientes.");
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        barbeiroDormindo = false;
        if (abortada || ocupadas == 0) {
            return -1;
        }
        if (clienteEmAtendimento != 0) {
            throw new IllegalStateException("O corte anterior ainda não terminou.");
        }

        int id = fila[inicio];
        fila[inicio] = 0;
        inicio = (inicio + 1) % cadeiras;
        ocupadas--;
        clienteEmAtendimento = id;
        log("Barbeiro chamou o Cliente " + id + " (cadeiras ocupadas: " + ocupadas + "/" + cadeiras + ")");
        return id;
    }

    public synchronized void terminarCorte(int id) {
        validarId(id);
        if (abortada) {
            return;
        }
        if (clienteEmAtendimento != id) {
            throw new IllegalStateException("O Cliente " + id + " não está sendo atendido.");
        }
        clienteEmAtendimento = 0;
        if (!cancelados[id - 1]) {
            cortados[id - 1] = true;
            log("Barbeiro terminou o corte do Cliente " + id + ".");
        } else {
            log("Barbeiro liberou a cadeira do Cliente " + id + ", que cancelou o atendimento.");
        }
        notifyAll();
    }

    // Fechamento normal: não admite novos clientes, mas conclui os já admitidos.
    public synchronized void fechar() {
        aberta = false;
        notifyAll();
    }

    // Encerramento excepcional: nenhum cliente permanece esperando um corte.
    public synchronized void abortar(String motivo) {
        if (abortada) {
            return;
        }
        abortada = true;
        aberta = false;
        barbeiroDormindo = false;
        while (ocupadas > 0) {
            int id = fila[inicio];
            cancelados[id - 1] = true;
            fila[inicio] = 0;
            inicio = (inicio + 1) % cadeiras;
            ocupadas--;
        }
        if (clienteEmAtendimento != 0) {
            cancelados[clienteEmAtendimento - 1] = true;
            clienteEmAtendimento = 0;
        }
        log("BARBEARIA: atendimentos pendentes cancelados. " + motivo);
        notifyAll();
    }

    public synchronized boolean isAbortada() {
        return abortada;
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
                    // Fora do monitor: novos clientes podem chegar durante o corte.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    barbearia.abortar("Barbeiro interrompido durante o corte.");
                    Thread.currentThread().interrupt();
                    break;
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
                log("Cliente " + id + " cancelou antes de chegar.");
                return;
            }
            log("Cliente " + id + " chegou à barbearia.");
            barbearia.chegar(id);
        }
    }

    private static void mostrarUso(String erro) {
        System.err.println(erro);
        System.err.println("Uso: java Exercicio02.BarbeiroDorminhoco [clientes cadeiras]");
        System.err.println("Informe os dois parâmetros ou nenhum. Clientes >= 0; cadeiras de espera >= 1.");
    }

    // A limpeza precisa terminar mesmo se main receber mais de uma interrupção.
    private static void aguardarLimpeza(Thread[] clientes, Thread barbeiro) {
        boolean interrompida = false;
        for (Thread cliente : clientes) {
            boolean terminou = false;
            while (!terminou) {
                try {
                    cliente.join();
                    terminou = true;
                } catch (InterruptedException e) {
                    interrompida = true;
                }
            }
        }
        boolean terminou = false;
        while (!terminou) {
            try {
                barbeiro.join();
                terminou = true;
            } catch (InterruptedException e) {
                interrompida = true;
            }
        }
        if (interrompida) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        int clientes = 10;
        int cadeiras = 3;
        if (args.length != 0 && args.length != 2) {
            mostrarUso("Quantidade incorreta de parâmetros.");
            return;
        }
        if (args.length == 2) {
            try {
                clientes = Integer.parseInt(args[0]);
                cadeiras = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                mostrarUso("Os parâmetros devem ser inteiros válidos de 32 bits.");
                return;
            }
        }
        if (clientes < 0 || cadeiras < 1) {
            mostrarUso("Quantidade de clientes ou de cadeiras inválida.");
            return;
        }

        // Validação e criação acontecem antes de iniciar qualquer thread.
        BarbeiroDorminhoco barbearia = new BarbeiroDorminhoco(cadeiras, clientes);
        Thread barbeiro = new Thread(new Barbeiro(barbearia), "Barbeiro");
        Thread[] threadsClientes = new Thread[clientes];
        for (int i = 0; i < clientes; i++) {
            threadsClientes[i] = new Thread(new Cliente(i + 1, barbearia), "Cliente " + (i + 1));
        }
        System.out.println("Barbearia aberta: " + clientes + " clientes, " + cadeiras + " cadeiras de espera\n");

        barbeiro.start();
        for (Thread cliente : threadsClientes) {
            cliente.start();
        }
        try {
            for (Thread cliente : threadsClientes) {
                cliente.join();
            }
            barbearia.fechar();
            barbeiro.join();
        } catch (InterruptedException e) {
            barbearia.abortar("Thread principal interrompida.");
            for (Thread cliente : threadsClientes) {
                cliente.interrupt();
            }
            barbeiro.interrupt();
            aguardarLimpeza(threadsClientes, barbeiro);
            Thread.currentThread().interrupt();
        }
        System.out.println(barbearia.isAbortada()
                ? "\nPrograma finalizado com cancelamento."
                : "\nPrograma finalizado.");
    }
}
