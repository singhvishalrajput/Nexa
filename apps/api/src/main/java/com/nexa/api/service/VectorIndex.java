package com.nexa.api.service;
import com.nexa.api.beans.Intent;


import java.util.*;

public interface VectorIndex {
  record Entry(Intent intent, double[] vector) {}

  record Hit(Intent intent, double score) {}

  List<Hit> search(double[] vector);
}
