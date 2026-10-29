import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ApiError } from "../api/client";
import type {
  AcademicSubject,
  AcademicSubjectRequest,
  AcademicYear,
  AcademicYearRequest,
  OfficialGrade,
  Semester,
  SemesterRequest,
} from "../types";
import { Button, FieldError } from "./ui";

const dateRangeSchema = (name: string, maximumLength: number) => z.object({
  label: z.string().trim().min(1, `${name} is required`).max(maximumLength, `Use at most ${maximumLength} characters`),
  startDate: z.string(),
  endDate: z.string(),
}).refine(
  (value) => !value.startDate || !value.endDate || value.endDate >= value.startDate,
  { path: ["endDate"], message: "End date cannot be earlier than start date" },
);

export function AcademicYearForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: AcademicYear;
  onSubmit: (request: AcademicYearRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const schema = dateRangeSchema("Academic year", 40);
  type Values = z.infer<typeof schema>;
  const [submitError, setSubmitError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    defaultValues: {
      label: initial?.label || "",
      startDate: initial?.startDate || "",
      endDate: initial?.endDate || "",
    },
  });

  const save = async (values: Values) => {
    setSubmitError("");
    const parsed = schema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof Values;
        if (key) setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit({
        label: parsed.data.label,
        startDate: parsed.data.startDate || null,
        endDate: parsed.data.endDate || null,
      });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Unable to save this academic year.");
    }
  };

  return (
    <form className="form-grid academic-form" onSubmit={(event) => void handleSubmit(save)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field">
        <span>Academic year label <b aria-hidden="true">*</b></span>
        <input {...register("label")} aria-required="true" maxLength={40} placeholder="2026-2027" />
        <FieldError>{errors.label?.message}</FieldError>
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
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : initial ? "Save changes" : "Create year"}</Button>
      </div>
    </form>
  );
}

export function SemesterForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: Semester;
  onSubmit: (request: SemesterRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const schema = dateRangeSchema("Semester", 80);
  type Values = z.infer<typeof schema>;
  const [submitError, setSubmitError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    defaultValues: {
      label: initial?.label || "",
      startDate: initial?.startDate || "",
      endDate: initial?.endDate || "",
    },
  });

  const save = async (values: Values) => {
    setSubmitError("");
    const parsed = schema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof Values;
        if (key) setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit({
        label: parsed.data.label,
        startDate: parsed.data.startDate || null,
        endDate: parsed.data.endDate || null,
      });
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Unable to save this semester.");
    }
  };

  return (
    <form className="form-grid academic-form" onSubmit={(event) => void handleSubmit(save)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field">
        <span>Semester label <b aria-hidden="true">*</b></span>
        <input {...register("label")} aria-required="true" maxLength={80} placeholder="Semester 1" />
        <FieldError>{errors.label?.message}</FieldError>
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
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : initial ? "Save changes" : "Create semester"}</Button>
      </div>
    </form>
  );
}

const subjectSchema = z.object({
  name: z.string().trim().min(1, "Subject name is required").max(150, "Use at most 150 characters"),
  code: z.string().max(30, "Use at most 30 characters"),
  credits: z.string().regex(/^[1-9]\d*$/, "Credits must be a positive whole number"),
  maximumMarks: z.string().refine(
    (value) => /^\d+(\.\d{1,2})?$/.test(value) && Number.isFinite(Number(value)) && Number(value) > 0,
    "Maximum marks must be positive and use at most two decimal places",
  ),
  obtainedMarks: z.string().refine(
    (value) => value === "" || (/^\d+(\.\d{1,2})?$/.test(value) && Number.isFinite(Number(value)) && Number(value) >= 0),
    "Obtained marks must be non-negative and use at most two decimal places",
  ),
  officialGrade: z.enum(["", "O", "A+", "A", "B+", "B", "C", "U"]),
}).refine(
  (values) => !values.obtainedMarks || !values.maximumMarks
    || Number(values.obtainedMarks) <= Number(values.maximumMarks),
  { path: ["obtainedMarks"], message: "Obtained marks cannot exceed maximum marks" },
);

type SubjectValues = z.infer<typeof subjectSchema>;

const grades: Array<{ value: OfficialGrade; points: number }> = [
  { value: "O", points: 10 },
  { value: "A+", points: 9 },
  { value: "A", points: 8 },
  { value: "B+", points: 7 },
  { value: "B", points: 6 },
  { value: "C", points: 5 },
  { value: "U", points: 0 },
];

export function AcademicSubjectForm({
  initial,
  onSubmit,
  onCancel,
}: {
  initial?: AcademicSubject;
  onSubmit: (request: AcademicSubjectRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [submitError, setSubmitError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<SubjectValues>({
    defaultValues: {
      name: initial?.name || "",
      code: initial?.code || "",
      credits: initial ? String(initial.credits) : "",
      maximumMarks: initial ? String(initial.maximumMarks) : "",
      obtainedMarks: initial?.obtainedMarks == null ? "" : String(initial.obtainedMarks),
      officialGrade: initial?.officialGrade || "",
    },
  });
  const selectedGrade = watch("officialGrade");

  const save = async (values: SubjectValues) => {
    setSubmitError("");
    const parsed = subjectSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof SubjectValues;
        if (key) setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit({
        name: parsed.data.name,
        code: parsed.data.code.trim() || null,
        credits: Number(parsed.data.credits),
        maximumMarks: Number(parsed.data.maximumMarks),
        obtainedMarks: parsed.data.obtainedMarks === "" ? null : Number(parsed.data.obtainedMarks),
        officialGrade: parsed.data.officialGrade === "" ? null : parsed.data.officialGrade,
      });
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) setError(field as keyof SubjectValues, { message });
        }
      }
      setSubmitError(error instanceof Error ? error.message : "Unable to save this subject.");
    }
  };

  return (
    <form className="form-grid academic-form" onSubmit={(event) => void handleSubmit(save)(event)}>
      {submitError && <div className="alert alert-error field-span" role="alert">{submitError}</div>}
      <label className="field field-span">
        <span>Subject name <b aria-hidden="true">*</b></span>
        <input {...register("name")} aria-required="true" maxLength={150} />
        <FieldError>{errors.name?.message}</FieldError>
      </label>
      <label className="field">
        <span>Subject code</span>
        <input {...register("code")} maxLength={30} />
        <FieldError>{errors.code?.message}</FieldError>
      </label>
      <label className="field">
        <span>Credits <b aria-hidden="true">*</b></span>
        <input type="number" step="1" aria-required="true" {...register("credits")} />
        <FieldError>{errors.credits?.message}</FieldError>
      </label>
      <label className="field">
        <span>Maximum marks <b aria-hidden="true">*</b></span>
        <input type="number" step="0.01" aria-required="true" {...register("maximumMarks")} />
        <FieldError>{errors.maximumMarks?.message}</FieldError>
      </label>
      <label className="field">
        <span>Obtained marks</span>
        <input type="number" step="0.01" {...register("obtainedMarks")} />
        <FieldError>{errors.obtainedMarks?.message}</FieldError>
      </label>
      <label className="field">
        <span>Official letter grade</span>
        <select {...register("officialGrade")}>
          <option value="">Not recorded</option>
          {grades.map(({ value, points }) => <option key={value} value={value}>{value} · {points} points</option>)}
        </select>
        {selectedGrade && <small>R-2023 CBCCS: {selectedGrade} = {grades.find(({ value }) => value === selectedGrade)?.points} grade points.</small>}
      </label>
      <div className="form-actions field-span">
        <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? "Saving…" : initial ? "Save changes" : "Add subject"}</Button>
      </div>
    </form>
  );
}
