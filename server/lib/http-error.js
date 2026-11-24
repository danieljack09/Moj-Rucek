'use strict';

class HttpError extends Error {
  constructor(status, message, extra = {}) {
    super(message);
    this.status = status;
    this.extra = extra;   // machine-readable fields sent next to the message (for example a code)
  }
}

module.exports = { HttpError };
