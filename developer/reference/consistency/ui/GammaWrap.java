package consistency.ui;

import ensdfparser.ensdf.Gamma;

public class GammaWrap {
    Gamma gamma;

    /** Creates a new instance of EnsdfWrap */
    public GammaWrap(Gamma gam) {
        gamma = gam;
    }
    /// Return a nice string for the list box.
    public String toString(){
        return gamma.nucleus().nameENSDF()+": "+gamma.ES();
    }
    


}
