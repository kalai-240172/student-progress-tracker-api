import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, BookOpen, FileText, Plus, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { RecordRequest, StudentRecord } from "../types";
import { RecordForm } from "../components/RecordForm";
import { Button, EmptyState, ErrorState, formatDate, formatLabel, LoadingState, PageHeader, StatusBadge } from "../components/ui";

export function RecordsPage() {
  const [records, setRecords] = useState<StudentRecord[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const load = () => {
    setError(null);
    void api.listRecords().then(setRecords).catch(setError);
  };
  useEffect(load, []);

  if (error) return <><PageHeader eyebrow="YOUR JOURNEY" title="Student records" description="Keep important milestones, projects, and achievements together." /><ErrorState error={error} onRetry={load} /></>;
  if (!records) return <LoadingState label="Loading your records…" />;

  return (
    <>
      <PageHeader
        eyebrow="YOUR JOURNEY"
        title="Student records"
        description="A thoughtful archive of the work you’ve done."
        action={<Link className="button button-primary" to="/records/new"><Plus size={17} /> Add record</Link>}
      />
      {records.length === 0 ? (
        <div className="panel">
          <EmptyState icon={<FileText size={23} />} title="Your record book is empty" description="Add a course, certificate, project, or achievement to get started." action={<Link className="button button-primary" to="/records/new"><Plus size={17} /> Add your first record</Link>} />
        </div>
      ) : (
        <div className="record-grid">
          {records.map((record) => (
            <article className="record-card" key={record.id}>
              <div className="record-card-top">
                <span className="record-category-icon"><BookOpen size={17} /></span>
                <StatusBadge status={record.status} />
              </div>
              <p className="record-category">{formatLabel(record.category)}</p>
              <h2><Link to={`/records/${record.id}`}>{record.title}</Link></h2>
              <p className="record-institution">{record.institution || "Personal record"}</p>
              <div className="record-card-bottom">
                <span>{record.startDate ? formatDate(record.startDate) : "Date not set"}{record.endDate ? ` – ${formatDate(record.endDate)}` : ""}</span>
                <Link to={`/records/${record.id}`} className="icon-link" aria-label={`View ${record.title}`}><ArrowRight size={17} /></Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}

export function RecordFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [record, setRecord] = useState<StudentRecord | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(Boolean(id));

  useEffect(() => {
    if (!id) return;
    const recordId = Number(id);
    if (!Number.isSafeInteger(recordId) || recordId <= 0) {
      setError(new Error("Invalid record address."));
      setLoading(false);
      return;
    }
    void api.getRecord(recordId).then(setRecord).catch(setError).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <LoadingState label="Loading record…" />;
  if (error && id) return <><PageHeader title="Record" /><ErrorState error={error} /></>;

  const save = async (request: RecordRequest) => {
    if (id) await api.updateRecord(Number(id), request);
    else await api.createRecord(request);
    navigate("/records");
  };

  return (
    <>
      <Link to="/records" className="back-link"><ArrowLeft size={16} /> Back to records</Link>
      <PageHeader eyebrow="STUDENT RECORDS" title={id ? "Edit record" : "Add a record"} description="Capture the details you’ll want to remember." />
      <div className="form-panel panel">
        {error !== null && <ErrorState error={error} />}
        <RecordForm initial={record || undefined} onSubmit={save} onCancel={() => navigate("/records")} />
      </div>
    </>
  );
}

export function RecordDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [record, setRecord] = useState<StudentRecord | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const recordId = Number(id);
    if (!Number.isSafeInteger(recordId) || recordId <= 0) {
      setError(new Error("Invalid record address."));
      return;
    }
    void api.getRecord(recordId).then(setRecord).catch(setError);
  }, [id]);

  const remove = async () => {
    if (!record || !window.confirm(`Delete “${record.title}”? This cannot be undone.`)) return;
    setDeleting(true);
    try {
      await api.deleteRecord(record.id);
      navigate("/records");
    } catch (deleteError) {
      setError(deleteError);
      setDeleting(false);
    }
  };

  if (error) return <><PageHeader title="Student record" /><ErrorState error={error} /></>;
  if (!record) return <LoadingState label="Loading record…" />;

  return (
    <>
      <Link to="/records" className="back-link"><ArrowLeft size={16} /> Back to records</Link>
      <PageHeader
        eyebrow={formatLabel(record.category)}
        title={record.title}
        description={record.institution || "Personal student record"}
        action={<div className="header-actions"><Link className="button button-secondary" to={`/records/${record.id}/edit`}>Edit record</Link><Button variant="danger" onClick={() => void remove()} disabled={deleting}><Trash2 size={16} /> {deleting ? "Deleting…" : "Delete"}</Button></div>}
      />
      {error && <ErrorState error={error} />}
      <section className="detail-grid">
        <div className="panel detail-main">
          <div className="detail-section-title"><span>ABOUT THIS RECORD</span><StatusBadge status={record.status} /></div>
          <p className="detail-description">{record.description || "No description was added for this record."}</p>
          <div className="detail-facts">
            <DetailFact label="Category" value={formatLabel(record.category)} />
            <DetailFact label="Institution" value={record.institution || "—"} />
            <DetailFact label="Start date" value={formatDate(record.startDate)} />
            <DetailFact label="End date" value={formatDate(record.endDate)} />
          </div>
        </div>
        <aside className="panel detail-side">
          <h2>Record details</h2>
          <DetailFact label="Created" value={formatDate(record.createdAt)} />
          <DetailFact label="Last updated" value={formatDate(record.updatedAt)} />
          <div className="side-note"><FileText size={16} /> Your records are private to your account.</div>
        </aside>
      </section>
    </>
  );
}

function DetailFact({ label, value }: { label: string; value: string }) {
  return <div className="detail-fact"><span>{label}</span><strong>{value}</strong></div>;
}
