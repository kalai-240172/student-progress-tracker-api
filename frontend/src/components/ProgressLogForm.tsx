import { z } from "zod";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { ApiError } from "../api/client";
import type { ProgressLog, ProgressLogRequest } from "../types";
import { Button, FieldError } from "./ui";

const logSchema = z.object({
  activityDate: z.string().min(1, "Activity date is required"),
  timeSpentMinutes: z.number().int().positive("Time must be greater than zero").max(2147483647, "Time value is too large"),
  summary: z.string().trim().min(1, "Summary is required").max(2000, "Use at most 2,000 characters"),
  notes: z.string().max(2000, "Use at most 2,000 characters"),
});
type FormValues = z.infer<typeof logSchema>;

function localDateToday(): string {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 10);
}

export function ProgressLogForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: ProgressLog;
  onSubmit: (request: ProgressLogRequest) => Promise<void>;
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
      activityDate: initial?.activityDate || localDateToday(),
      timeSpentMinutes: initial?.timeSpentMinutes || 30,
      summary: initial?.summary || "",
      notes: initial?.notes || "",
    },
  });

  const submit = async (values: FormValues) => {
    setSubmitError("");
    const parsed = logSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FormValues;
        if (key) setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit({ ...parsed.data, notes: parsed.data.notes || null });
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) setError(field as keyof FormValues, { message });
        }
      }
      setSubmitError(error instanceof Error ? error.message : "Unable to save this progress entry.");
    }
  };

  return (
    <form className="form-grid" onSubmit={(event) => void handleSubmit(submit)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field">
        <span>Activity date <b aria-hidden="true">*</b></span>
        <input type="date" {...register("activityDate")} />
        <FieldError>{errors.activityDate?.message}</FieldError>
      </label>
      <label className="field">
        <span>Minutes spent <b aria-hidden="true">*</b></span>
        <input type="number" min={1} step={1} {...register("timeSpentMinutes", { valueAsNumber: true })} />
        <FieldError>{errors.timeSpentMinutes?.message}</FieldError>
      </label>
      <label className="field field-span">
        <span>What did you work on? <b aria-hidden="true">*</b></span>
        <textarea {...register("summary")} rows={3} maxLength={2000} />
        <FieldError>{errors.summary?.message}</FieldError>
      </label>
      <label className="field field-span">
        <span>Notes</span>
        <textarea {...register("notes")} rows={2} maxLength={2000} />
        <FieldError>{errors.notes?.message}</FieldError>
      </label>
      <div className="form-actions field-span">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : initial ? "Update log" : "Add progress"}
        </Button>
      </div>
    </form>
  );
}
