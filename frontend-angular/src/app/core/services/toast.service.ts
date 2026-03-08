import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type ToastTone = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: number;
  tone: ToastTone;
  text: string;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private readonly toastsSubject = new BehaviorSubject<ToastMessage[]>([]);
  private idSequence = 0;

  readonly toasts$ = this.toastsSubject.asObservable();

  showSuccess(text: string, durationMs = 3200): void {
    this.push('success', text, durationMs);
  }

  showError(text: string, durationMs = 4200): void {
    this.push('error', text, durationMs);
  }

  showInfo(text: string, durationMs = 3000): void {
    this.push('info', text, durationMs);
  }

  dismiss(id: number): void {
    this.toastsSubject.next(this.toastsSubject.value.filter((toast) => toast.id !== id));
  }

  private push(tone: ToastTone, text: string, durationMs: number): void {
    const nextId = ++this.idSequence;
    const nextToast: ToastMessage = {
      id: nextId,
      tone,
      text
    };
    this.toastsSubject.next([...this.toastsSubject.value, nextToast]);

    if (durationMs > 0) {
      window.setTimeout(() => this.dismiss(nextId), durationMs);
    }
  }
}
