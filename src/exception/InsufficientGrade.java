package exception;

public class InsufficientGrade extends RuntimeException {
    private double notaObtinuta, notaDeTrecere = 5;
    public InsufficientGrade(double notaObtinuta) {
        super(String.format("Nu ati obtinut nota de trecere \n. Nota dumneavoastra este %.1f in timp ce nota de trecere este 5.00",notaObtinuta));
        this.notaObtinuta = notaObtinuta;
    }
    public InsufficientGrade(double notaObtinuta,double notaDeTrecere) {
        super(String.format("Nu ati obtinut nota de trecere \n. Nota dumneavoastra este %.1f in timp ce nota de trecere este %.1f",notaObtinuta,notaDeTrecere));
        this.notaObtinuta = notaObtinuta;
        this.notaDeTrecere = notaDeTrecere;
    }
}
