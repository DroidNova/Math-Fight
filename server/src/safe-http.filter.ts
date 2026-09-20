import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus } from '@nestjs/common';
import { safeError } from './security';

type SafeResponse = {
  status(code: number): SafeResponse;
  json(body: unknown): void;
};

@Catch()
export class SafeHttpExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const response = host.switchToHttp().getResponse<SafeResponse>();
    const status = exception instanceof HttpException ? exception.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    if (status === HttpStatus.SERVICE_UNAVAILABLE || status >= 500) {
      response.status(HttpStatus.SERVICE_UNAVAILABLE).json(safeError('SERVICE_UNAVAILABLE'));
    } else {
      response.status(status).json(safeError('INVALID_PAYLOAD'));
    }
  }
}
