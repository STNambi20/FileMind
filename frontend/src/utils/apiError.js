export function getApiErrorMessage(error, fallbackMessage) {
  if (error?.code === "ECONNABORTED") {
    return "The server took too long to respond. Please try again.";
  }
  if (error?.message === "Network Error") {
    return "We couldn't reach the server. Check the connection and try again.";
  }
  const backendMessage = error?.response?.data?.message;
  const validationErrors = error?.response?.data?.data;
  if (backendMessage) {
    return backendMessage;
  }
  if (validationErrors && typeof validationErrors === "object") {
    const firstValidationError = Object.values(validationErrors)[0];
    if (firstValidationError) {
      return firstValidationError;
    }
  }
  return fallbackMessage;
}
