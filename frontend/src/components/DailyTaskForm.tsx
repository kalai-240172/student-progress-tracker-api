import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ApiError } from "../api/client";
import type { DailyTask, DailyTaskRequest } from "../types";
import { Button, FieldError } from "./ui";

const taskSchema = z.object({
  title: z.string().trim().min(1, "Title is required").max(150, "Use at most 150 characters"),
  description: z.string().max(2000, "Use at most 2000 characters"),
  subject: z.string().trim().min(1, "Subject is required").max(100, "Use at most 100 characters"),
  plannedDate: z.string().min(1, "Planned date is required"),
  estimatedDurationMinutes: z.coerce.number().int().positive("Estimated duration must be greater than zero"),
});

type FormValues = z.input<typeof taskSchema>;

function todayLocalDate(): string {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${today.getFullYear()}-${month}-${day}`;
}

export function DailyTaskForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: DailyTask;
  onSubmit: (request: DailyTaskRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [submitError, setSubmitError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: {
      title: initial?.title || "",
      description: initial?.description || "",
      subject: initial?.subject || "",
      plannedDate: initial?.plannedDate || todayLocalDate(),
      estimatedDurationMinutes: initial?.estimatedDurationMinutes || 30,
    },
  });

  const submit = async (values: FormValues) => {
    setSubmitError("");
    const parsed = taskSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FormValues;
        if (key) setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit({
        ...parsed.data,
        description: parsed.data.description || null,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) setError(field as keyof FormValues, { message });
        }
      }
      setSubmitError(error instanceof Error ? error.message : "Unable to save this task.");
    }
  };

  return (
    <form className="form-grid task-form" onSubmit={(event) => void handleSubmit(submit)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field">
        <span>Task title <b aria-hidden="true">*</b></span>
        <input {...register("title")} maxLength={150} autoFocus />
        <FieldError>{errors.title?.message}</FieldError>
      </label>
      <label className="field">
        <span>Subject <b aria-hidden="true">*</b></span>
        <input {...register("subject")} maxLength={100} />
        <FieldError>{errors.subject?.message}</FieldError>
      </label>
      <label className="field field-span">
        <span>Description</span>
        <textarea {...register("description")} rows={3} maxLength={2000} />
        <FieldError>{errors.description?.message}</FieldError>
      </label>
      <label className="field">
        <span>Planned date <b aria-hidden="true">*</b></span>
        <input type="date" {...register("plannedDate")} />
        <FieldError>{errors.plannedDate?.message}</FieldError>
      </label>
      <label className="field">
        <span>Estimated duration (minutes) <b aria-hidden="true">*</b></span>
        <input type="number" min={1} step={1} {...register("estimatedDurationMinutes")} />
        <FieldError>{errors.estimatedDurationMinutes?.message}</FieldError>
      </label>
      <div className="form-actions field-span">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : initial ? "Save changes" : "Create task"}
        </Button>
      </div>
    </form>
  );
}
