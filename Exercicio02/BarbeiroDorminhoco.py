import random
import sys
import time
from threading import Condition, Lock, RLock, Thread


trava_log = Lock()


def log(texto):
    with trava_log:
        print(texto, flush=True)


class Barbearia:
    def __init__(self, cadeiras, total_clientes):
        self.cadeiras = cadeiras
        self.fila = [0] * cadeiras
        self.inicio = 0
        self.ocupadas = 0
        self.cortados = [False] * (total_clientes + 1)
        self.barbeiro_dormindo = False
        self.aberta = True
        self.condicao = Condition(RLock())

    # Executado pela thread do cliente.
    def chegar(self, id):
        with self.condicao:
            if not self.aberta:
                log(f"Cliente {id} encontrou a barbearia FECHADA e foi embora.")
                return False
            if self.ocupadas >= self.cadeiras:
                log(f"Cliente {id} encontrou a barbearia CHEIA e foi embora.")
                return False

            self.fila[(self.inicio + self.ocupadas) % self.cadeiras] = id
            self.ocupadas += 1
            log(f"Cliente {id} sentou (cadeiras ocupadas: "
                f"{self.ocupadas}/{self.cadeiras})")
            if self.barbeiro_dormindo:
                self.barbeiro_dormindo = False
                log(f"Cliente {id} ACORDOU o barbeiro.")
            self.condicao.notify_all()

            # wait() libera o monitor enquanto este cliente aguarda o corte.
            while not self.cortados[id]:
                self.condicao.wait()
            log(f"Cliente {id} saiu com o cabelo cortado.")
            return True

    # Executado pela thread do barbeiro.
    def proximo_cliente(self):
        with self.condicao:
            while self.ocupadas == 0:
                if not self.aberta:
                    return -1
                self.barbeiro_dormindo = True
                log("Barbeiro: não há clientes... vai DORMIR (zzz)")
                self.condicao.wait()

            self.barbeiro_dormindo = False
            id = self.fila[self.inicio]
            self.inicio = (self.inicio + 1) % self.cadeiras
            self.ocupadas -= 1
            log(f"Barbeiro chamou o Cliente {id} (cadeiras ocupadas: "
                f"{self.ocupadas}/{self.cadeiras})")
            return id

    def terminar_corte(self, id):
        with self.condicao:
            self.cortados[id] = True
            self.condicao.notify_all()

    def fechar(self):
        with self.condicao:
            self.aberta = False
            self.condicao.notify_all()


class Barbeiro(Thread):
    def __init__(self, barbearia):
        super().__init__(name="Barbeiro")
        self.barbearia = barbearia

    def run(self):
        while True:
            id = self.barbearia.proximo_cliente()
            if id < 0:
                break
            log(f"Barbeiro CORTANDO o cabelo do Cliente {id}...")
            time.sleep(4)  # Corte fora do monitor: outros clientes podem chegar.
            self.barbearia.terminar_corte(id)
        log("Barbeiro foi para casa.")


class Cliente(Thread):
    def __init__(self, id, barbearia):
        super().__init__(name=f"Cliente {id}")
        self.id = id
        self.barbearia = barbearia

    def run(self):
        time.sleep(random.randrange(5000) / 1000)
        log(f"Cliente {self.id} chegou à barbearia.")
        self.barbearia.chegar(self.id)


def main(argumentos=None):
    argumentos = sys.argv[1:] if argumentos is None else argumentos
    if len(argumentos) not in (0, 2):
        print("Uso: python3 barbeiro_dorminhoco.py <clientes> <cadeiras>")
        return 1
    try:
        clientes, cadeiras = map(int, argumentos) if argumentos else (6, 3)
    except ValueError:
        print("Os parâmetros devem ser números inteiros.")
        return 1
    if clientes < 1 or cadeiras < 1:
        print("A quantidade de clientes e de cadeiras deve ser >= 1.")
        return 1

    log(f"Barbearia aberta: {clientes} clientes, "
        f"{cadeiras} cadeiras de espera\n")
    barbearia = Barbearia(cadeiras, clientes)
    barbeiro = Barbeiro(barbearia)
    threads_clientes = [Cliente(i + 1, barbearia) for i in range(clientes)]
    iniciados = []
    codigo_saida = 0

    barbeiro.start()
    try:
        for cliente in threads_clientes:
            cliente.start()
            iniciados.append(cliente)
        for cliente in iniciados:
            cliente.join()
    except KeyboardInterrupt:
        codigo_saida = 130
        log("\nInterrupção solicitada. Finalizando os atendimentos aceitos.")
    finally:
        # Fecha para novas chegadas; o barbeiro ainda atende a fila existente.
        barbearia.fechar()
        for cliente in iniciados:
            cliente.join()
        barbeiro.join()

    log("\nPrograma finalizado.")
    return codigo_saida


if __name__ == "__main__":
    sys.exit(main())
