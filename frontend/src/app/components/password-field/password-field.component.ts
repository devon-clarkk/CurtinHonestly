import { AfterContentInit, Component, ElementRef, inject, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Wraps a password `<input>` and adds a show/hide toggle.
 *
 * Usage — the input stays yours, bindings and validation included:
 *
 * ```html
 * <app-password-field>
 *   <input type="password" name="password" [(ngModel)]="password" required />
 * </app-password-field>
 * ```
 *
 * It flips the projected input's `type` rather than swapping in a second
 * element, so ngModel, form validity, and focus all survive the toggle.
 *
 * Styles live in global `styles.css`, not here: projected content keeps the
 * *parent* component's encapsulation attribute, so this component's own
 * stylesheet could never reach the input it wraps.
 */
@Component({
  selector: 'app-password-field',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="password-field">
      <ng-content />
      <button
        type="button"
        class="password-toggle"
        [attr.aria-label]="visible() ? 'Hide password' : 'Show password'"
        [attr.aria-pressed]="visible()"
        (click)="toggle()">
        <app-icon [name]="visible() ? 'eye-off' : 'eye'" />
      </button>
    </div>
  `
})
export class PasswordFieldComponent implements AfterContentInit {
  private host = inject(ElementRef<HTMLElement>);

  visible = signal(false);
  private input: HTMLInputElement | null = null;

  ngAfterContentInit(): void {
    this.input = this.host.nativeElement.querySelector('input');
  }

  toggle(): void {
    this.visible.update(visible => !visible);
    if (this.input) {
      this.input.type = this.visible() ? 'text' : 'password';
    }
  }
}
