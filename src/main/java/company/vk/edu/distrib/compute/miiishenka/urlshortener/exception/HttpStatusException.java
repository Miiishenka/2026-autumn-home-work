package company.vk.edu.distrib.compute.miiishenka.urlshortener.exception;

public abstract class HttpStatusException extends Exception {
    public abstract int getStatusCode();
}
