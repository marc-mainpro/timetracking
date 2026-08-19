import { Component, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { relativeTime } from '../../core/relative-time';
import { ErrorMessagesService } from '../../core/services/error-messages.service';
import { FailedQueueEntry, FailedQueueService, PagedFailedQueueEntries } from './failed-queue.service';

export type QueueActionKind = 'retry' | 'discard';

/**
 * Acción a la espera de confirmación. Reintentar vuelve a lanzar trabajo real
 * contra sistemas externos y descartar renuncia a él para siempre: ninguna de
 * las dos debería depender de un `window.confirm` sin contexto.
 */
interface PendingAction {
  readonly entry: FailedQueueEntry;
  readonly kind: QueueActionKind;
  readonly title: string;
  readonly consequence: string;
  readonly confirmLabel: string;
  readonly reason: 'required' | 'none';
  readonly danger: boolean;
}

const PAGE_SIZE = 10;

/**
 * Elementos de una cola que agotaron sus reintentos, con las dos únicas salidas
 * que tienen: volver a intentarlo o abandonarlo dejando constancia.
 *
 * <p>Vive dentro del panel de estado y no en una ruta propia: quien abre esa
 * pantalla viene a resolver una incidencia concreta, y separarlo le obligaría a
 * perder de vista el veredicto que le trajo hasta aquí.
 */
@Component({
  selector: 'app-failed-queue',
  imports: [ReactiveFormsModule],
  templateUrl: './failed-queue.component.html',
  styleUrl: './failed-queue.component.scss'
})
export class FailedQueueComponent {
  private readonly failedQueueService = inject(FailedQueueService);
  private readonly errorMessagesService = inject(ErrorMessagesService);
  private readonly fb = inject(FormBuilder);

  private readonly confirmDialog = viewChild<ElementRef<HTMLDialogElement>>('confirmDialog');

  /** Cola a mostrar. Cambiarla recarga el listado desde la primera página. */
  readonly queue = input.required<string>();

  /** Algo cambió en la cola: el panel debe recalcular sus contadores. */
  readonly changed = output<void>();

  readonly loading = signal(false);
  readonly acting = signal(false);
  readonly page = signal(0);
  readonly result = signal<PagedFailedQueueEntries | null>(null);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly pendingAction = signal<PendingAction | null>(null);

  /** «Ahora» congelado en cada carga; ver `relative-time`. */
  private readonly renderedAt = signal(Date.now());

  readonly reasonControl = this.fb.nonNullable.control('', Validators.required);

  constructor() {
    effect(() => {
      // Leer la señal de entrada suscribe el efecto: al cambiar de cola se
      // vuelve a la primera página, porque la anterior no significa nada aquí.
      this.queue();
      this.page.set(0);
      this.load(0);
    });
  }

  // --- Datos ---------------------------------------------------------------

  /**
   * @param keepError conserva el error ya mostrado. Lo usa la recarga que sigue
   *     a una acción fallida: la lista hay que refrescarla igualmente, pero
   *     borrar el motivo del fallo dejaría al usuario sin saber qué pasó.
   */
  load(page = this.page(), keepError = false): void {
    this.loading.set(true);
    if (!keepError) {
      this.error.set(null);
    }
    this.failedQueueService.list(this.queue(), page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.result.set(result);
        this.renderedAt.set(Date.now());
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.errorMessagesService.fromProblem(err.error));
      }
    });
  }

  nextPage(): void {
    const result = this.result();
    if (result && result.page + 1 < result.totalPages) {
      this.page.update((page) => page + 1);
      this.load(this.page());
    }
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.page.update((page) => page - 1);
      this.load(this.page());
    }
  }

  // --- Acciones ------------------------------------------------------------

  askRetry(entry: FailedQueueEntry): void {
    this.ask({
      entry,
      kind: 'retry',
      title: 'Reintentar este elemento',
      consequence: 'Vuelve a la cola y se intenta de nuevo en la próxima pasada.',
      confirmLabel: 'Reintentar',
      reason: 'none',
      danger: false
    });
  }

  askDiscard(entry: FailedQueueEntry): void {
    this.ask({
      entry,
      kind: 'discard',
      title: 'Descartar este elemento',
      consequence: 'Deja de intentarse. Se conserva el registro y queda constancia de quién y por qué.',
      confirmLabel: 'Descartar',
      reason: 'required',
      danger: true
    });
  }

  confirmAction(): void {
    const action = this.pendingAction();
    if (!action || this.acting()) {
      return;
    }
    if (action.reason === 'required' && this.reasonControl.invalid) {
      this.reasonControl.markAsTouched();
      return;
    }

    this.acting.set(true);
    this.error.set(null);
    const request =
      action.kind === 'retry'
        ? this.failedQueueService.retry(this.queue(), action.entry.id)
        : this.failedQueueService.discard(this.queue(), action.entry.id, this.reasonControl.value);

    request.subscribe({
      next: () => {
        this.acting.set(false);
        this.closeDialog();
        this.message.set(
          action.kind === 'retry' ? 'Elemento devuelto a la cola.' : 'Elemento descartado.'
        );
        this.afterChange();
      },
      error: (err) => {
        this.acting.set(false);
        this.closeDialog();
        this.error.set(this.errorMessagesService.fromProblem(err.error));
        // Un 409 significa que otra persona actuó antes: la lista que se está
        // viendo ya es falsa, así que se recarga en lugar de dejarla mentir.
        this.afterChange(true);
      }
    });
  }

  cancelAction(): void {
    this.closeDialog();
  }

  // --- Presentación --------------------------------------------------------

  /** «hace 2 horas»: la antigüedad importa más que el instante exacto. */
  age(entry: FailedQueueEntry): string {
    return relativeTime(entry.occurredAt, this.renderedAt());
  }

  private ask(action: PendingAction): void {
    this.message.set(null);
    this.reasonControl.reset('');
    this.pendingAction.set(action);
    this.confirmDialog()?.nativeElement.showModal();
  }

  private closeDialog(): void {
    this.confirmDialog()?.nativeElement.close();
    this.pendingAction.set(null);
  }

  /**
   * Tras actuar, la página actual puede haberse quedado vacía: si era la
   * última, retroceder evita mostrar un listado en blanco que parece un fallo.
   */
  private afterChange(keepError = false): void {
    const result = this.result();
    if (result && result.content.length === 1 && this.page() > 0) {
      this.page.update((page) => page - 1);
    }
    this.load(this.page(), keepError);
    this.changed.emit();
  }
}
