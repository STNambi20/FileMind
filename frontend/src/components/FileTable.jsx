import { Link } from "react-router-dom";

function FileTable({ files }) {
  return (
    <div className="card overflow-hidden p-0">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              {["Name", "Owner", "Category", "Versions", "Last Updated", "Action"].map((heading) => (
                <th
                  key={heading}
                  className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-[0.15em] text-slate-500"
                >
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200 bg-white">
            {files.map((file) => (
              <tr key={file.id} className="hover:bg-slate-50">
                <td className="px-6 py-4">
                  <p className="font-medium text-slate-900">{file.name}</p>
                  <p className="text-sm text-slate-500">{file.summary}</p>
                </td>
                <td className="px-6 py-4 text-sm text-slate-600">{file.owner}</td>
                <td className="px-6 py-4 text-sm text-slate-600">{file.category}</td>
                <td className="px-6 py-4 text-sm text-slate-600">{file.versionCount}</td>
                <td className="px-6 py-4 text-sm text-slate-600">{file.updatedAt}</td>
                <td className="px-6 py-4 text-sm">
                  <Link className="font-medium text-brand-700 hover:text-brand-800" to={`/files/${file.id}`}>
                    View details
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default FileTable;

