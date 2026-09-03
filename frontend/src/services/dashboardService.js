import api from "./api";

export async function getDashboard() {
  const { data } = await api.get("/dashboard", { timeout: 10000 });
  return data.data;
}

export async function getRecentActivity() {
  const { data } = await api.get("/activity/recent");
  return data.data;
}

