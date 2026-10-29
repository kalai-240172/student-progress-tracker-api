import { z } from "zod";
import { useForm } from "react-hook-form";
import { useState } from "react";
import { ApiError } from "../api/client";
import type { RecordCategory, RecordRequest, RecordStatus, StudentRecord } from "../types";
import { Button, FieldError } from "./ui";

const recordSchema = z
  .object({
    title: z.string().trim().min(1, "Title is required").max(150, "Use at most 150 characters"),
    description: z.string().max(2000, "Use at most 2000 characters"),
    category: z.enum(["ACADEMIC", "CERTIFICATE", "PROJECT", "ACHIEVEMENT"]),
    institution: z.string().max(200, "Use at most 200 characters"),
    startDate: z.string(),
    endDate: z.string(),
    status: z.enum(["IN_PROGRESS", "COMPLETED", "PLANNED"]),
  })
  .refine(
    (values) => !values.startDate || !values.endDate || values.endDate >= values.startDate,
    { path: ["endDate"], message: "End date cannot be earlier than start date" },
  );

type FormValues = z.infer<typeof recordSchema>;

const categories: RecordCategory[] = ["ACADEMIC", "CERTIFICATE", "PROJECT", "ACHIEVEMENT"];
const statuses: RecordStatus[] = ["IN_PROGRESS", "COMPLETED", "PLANNED"];

export function RecordForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: StudentRecord;
  onSubmit: (request: RecordRequest) => Promise<void>;
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
      category: initial?.category || "ACADEMIC",
      institution: initial?.institution || "",
      startDate: initial?.startDate || "",
      endDate: initial?.endDate || "",
      status: initial?.status || "IN_PROGRESS",
    },
  });

  const submit = async (values: FormValues) => {
    setSubmitError("");
    const parsed = recordSchema.safeParse(values);
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
        institution: parsed.data.institution || null,
        startDate: parsed.data.startDate || null,
        endDate: parsed.data.endDate || null,
      });
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) setError(field as keyof FormValues, { message });
        }
      }
      setSubmitError(error instanceof Error ? error.message : "Unable to save this record.");
    }
  };

  return (
    <form className="form-grid" onSubmit={(event) => void handleSubmit(submit)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field field-span">
        <span>Title <b aria-hidden="true">*</b></span>
        <input {...register("title")} maxLength={150} autoFocus />
        <FieldError>{errors.title?.message}</FieldError>
      </label>
      <label className="field field-span">
        <span>Description</span>
        <textarea {...register("description")} rows={4} maxLength={2000} />
        <small>Optional · up to 2,000 characters</small>
        <FieldError>{errors.description?.message}</FieldError>
      </label>
      <label className="field">
        <span>Category <b aria-hidden="true">*</b></span>
        <select {...register("category")}>
          {categories.map((category) => <option key={category} value={category}>{category.replace("_", " ")}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Status <b aria-hidden="true">*</b></span>
        <select {...register("status")}>
          {statuses.map((status) => <option key={status} value={status}>{status.replace("_", " ")}</option>)}
        </select>
      </label>
      <label className="field field-span">
        <span>Institution</span>
        <input {...register("institution")} maxLength={200} placeholder="School, company, or organization" />
        <FieldError>{errors.institution?.message}</FieldError>
      </label>
      <label className="field">
        <span>Start date</span>
        <input type="date" {...register("startDate")} />
      </label>
      <label className="field">
        <span>End date</span>
        <input type="date" {...register("endDate")} />
        <FieldError>{errors.endDate?.message}</FieldError>
      </label>
      <div className="form-actions field-span">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : initial ? "Save changes" : "Create record"}
        </Button>
      </div>
    </form>
  );
}
