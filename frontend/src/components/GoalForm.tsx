import { z } from "zod";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { ApiError } from "../api/client";
import type { Goal, GoalCategory, GoalRequest, GoalStatus, Weekday } from "../types";
import { Button, FieldError } from "./ui";

const weekdays: Weekday[] = [
  "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
];
const goalSchema = z.object({
  title: z.string().trim().min(1, "Title is required").max(150, "Use at most 150 characters"),
  description: z.string().max(2000, "Use at most 2000 characters"),
  category: z.enum(["STUDY", "PROJECT", "CAREER", "PERSONAL"]),
  targetDate: z.string(),
  targetDurationMinutes: z.number().int().positive("Target duration must be greater than zero").max(2147483647, "Target duration is too large"),
  preferredWeekdays: z.array(z.enum(weekdays)),
  status: z.enum(["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"]),
});

type FormValues = z.infer<typeof goalSchema>;
const categories: GoalCategory[] = ["STUDY", "PROJECT", "CAREER", "PERSONAL"];
const statuses: GoalStatus[] = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

export function GoalForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: Goal;
  onSubmit: (request: GoalRequest) => Promise<void>;
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
      category: initial?.category || "STUDY",
      targetDate: initial?.targetDate || "",
      targetDurationMinutes: initial?.targetDurationMinutes || 60,
      preferredWeekdays: initial?.preferredWeekdays || [],
      status: initial?.status || "NOT_STARTED",
    },
  });

  const submit = async (values: FormValues) => {
    setSubmitError("");
    const parsed = goalSchema.safeParse(values);
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
        targetDate: parsed.data.targetDate || null,
      });
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) setError(field as keyof FormValues, { message });
        }
      }
      setSubmitError(error instanceof Error ? error.message : "Unable to save this goal.");
    }
  };

  return (
    <form className="form-grid" onSubmit={(event) => void handleSubmit(submit)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field field-span">
        <span>Goal title <b aria-hidden="true">*</b></span>
        <input {...register("title")} maxLength={150} autoFocus />
        <FieldError>{errors.title?.message}</FieldError>
      </label>
      <label className="field field-span">
        <span>Description</span>
        <textarea {...register("description")} rows={3} maxLength={2000} />
        <FieldError>{errors.description?.message}</FieldError>
      </label>
      <label className="field">
        <span>Category <b aria-hidden="true">*</b></span>
        <select {...register("category")}>
          {categories.map((value) => <option key={value} value={value}>{value[0] + value.slice(1).toLowerCase()}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Status <b aria-hidden="true">*</b></span>
        <select {...register("status")}>
          {statuses.map((value) => <option key={value} value={value}>{value.replace("_", " ")}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Target duration (minutes) <b aria-hidden="true">*</b></span>
        <input
          type="number"
          min={1}
          step={1}
          {...register("targetDurationMinutes", { valueAsNumber: true })}
        />
        <FieldError>{errors.targetDurationMinutes?.message}</FieldError>
      </label>
      <label className="field">
        <span>Target date</span>
        <input type="date" {...register("targetDate")} />
      </label>
      <fieldset className="field field-span weekday-field">
        <legend>Preferred study days</legend>
        <div className="weekday-grid">
          {weekdays.map((day) => (
            <label key={day} className="check-option">
              <input type="checkbox" value={day} {...register("preferredWeekdays")} />
              <span>{day.slice(0, 3)}</span>
            </label>
          ))}
        </div>
        <FieldError>{errors.preferredWeekdays?.message}</FieldError>
      </fieldset>
      <div className="form-actions field-span">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : initial ? "Save changes" : "Create goal"}
        </Button>
      </div>
    </form>
  );
}
