function PageHeader({ title, subtitle, action }) {
  return (
    <div className="mb-6 flex flex-col gap-4 md:mb-8 md:flex-row md:items-end md:justify-between md:gap-6">
      <div className="min-w-0">
        <h2 className="page-title">{title}</h2>
        {subtitle ? <p className="page-subtitle">{subtitle}</p> : null}
      </div>
      {action ? <div className="w-full md:w-auto md:max-w-[28rem] md:shrink-0">{action}</div> : null}
    </div>
  );
}

export default PageHeader;
