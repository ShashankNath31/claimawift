import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

type ValidationMap = Record<string, string>;

interface ApiErrorEnvelope {
  code?: string;
  message?: string;
  data?: unknown;
}

export function extractValidationErrors(error: unknown): ValidationMap {
  const envelope = readEnvelope(error);
  if (envelope.code !== 'VALIDATION_ERROR') {
    return {};
  }
  if (!envelope.data || typeof envelope.data !== 'object' || Array.isArray(envelope.data)) {
    return {};
  }
  const raw = envelope.data as ValidationMap;
  const normalized: ValidationMap = {};
  for (const [key, value] of Object.entries(raw)) {
    const shortKey = key.includes('.') ? key.substring(key.lastIndexOf('.') + 1) : key;
    normalized[shortKey] = value;
  }
  return normalized;
}

export function toUserErrorMessage(
  error: unknown,
  fallback = 'Request failed. Please try again.',
  invalidInputMessage = 'Please check the entered values and try again.'
): string {
  const envelope = readEnvelope(error);
  const validationErrors = extractValidationErrors(error);
  if (Object.keys(validationErrors).length > 0) {
    return 'Please correct the highlighted fields.';
  }

  const message = envelope.message?.trim();
  if (message && !isGenericMessage(message)) {
    return message;
  }

  const status = readStatus(error);
  if (status === 0) {
    return 'Unable to reach the server. Check your network and try again.';
  }
  if (status === 400 || status === 422) {
    return invalidInputMessage;
  }
  if (status === 401) {
    return 'Authentication failed. Please verify your credentials.';
  }
  if (status === 403) {
    return 'You are not authorized to perform this action.';
  }
  if (status === 404) {
    return 'Requested resource was not found.';
  }

  return fallback;
}

export function getFieldError(
  form: FormGroup,
  field: string,
  label: string,
  serverErrors: ValidationMap = {}
): string {
  if (serverErrors[field]) {
    return serverErrors[field];
  }

  const control = form.get(field);
  if (!control || (!control.touched && !control.dirty) || !control.errors) {
    return '';
  }

  if (control.errors['required']) {
    return `${label} is required.`;
  }
  if (control.errors['email']) {
    return 'Enter a valid email address.';
  }
  if (control.errors['minlength']) {
    return `${label} must be at least ${control.errors['minlength'].requiredLength} characters.`;
  }
  if (control.errors['maxlength']) {
    return `${label} must be at most ${control.errors['maxlength'].requiredLength} characters.`;
  }
  if (control.errors['min']) {
    return `${label} must be at least ${control.errors['min'].min}.`;
  }
  if (control.errors['max']) {
    return `${label} must be at most ${control.errors['max'].max}.`;
  }

  return `Enter a valid ${label.toLowerCase()}.`;
}

function readEnvelope(error: unknown): ApiErrorEnvelope {
  if (error instanceof HttpErrorResponse) {
    return normalizeEnvelope(error.error);
  }
  return normalizeEnvelope((error as { error?: unknown })?.error);
}

function normalizeEnvelope(value: unknown): ApiErrorEnvelope {
  if (!value) {
    return {};
  }
  if (typeof value === 'string') {
    return { message: value };
  }
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value as ApiErrorEnvelope;
  }
  return {};
}

function readStatus(error: unknown): number | null {
  if (error instanceof HttpErrorResponse) {
    return error.status;
  }
  if (typeof error === 'object' && error && 'status' in error) {
    const status = (error as { status?: unknown }).status;
    return typeof status === 'number' ? status : null;
  }
  return null;
}

function isGenericMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return (
    normalized === 'an unexpected error occurred' ||
    normalized === 'internal server error' ||
    normalized === 'request failed' ||
    normalized === 'bad request'
  );
}
