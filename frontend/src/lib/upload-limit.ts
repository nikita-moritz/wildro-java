/**
 * What a phone photo may weigh before the server scales it down; the stored
 * picture is far smaller. One number for the server's check (lib/images.ts)
 * and the editor's, which refuses before anything is sent.
 */
export const MAX_UPLOAD = 8 * 1024 * 1024;
