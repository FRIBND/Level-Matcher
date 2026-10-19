import xgboost
import networkx
import pandas
import numpy
import sklearn

print("--- Package Status ---")
print(f"XGBoost:      {xgboost.__version__:<10} Location: {xgboost.__file__}")
print(f"NetworkX:     {networkx.__version__:<10} Location: {networkx.__file__}")
print(f"Pandas:       {pandas.__version__:<10} Location: {pandas.__file__}")
print(f"NumPy:        {numpy.__version__:<10} Location: {numpy.__file__}")
print(f"Scikit-learn: {sklearn.__version__:<10} Location: {sklearn.__file__}")