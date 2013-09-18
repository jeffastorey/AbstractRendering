#include <iostream>;
using namespace std;

float harlan_luminance(int len, float *A, float *B);

int main() {
  const int len = 1000;
  float A[len] = {2};
  float B[len*3] = {0};

  for (int i; i<len;i++) {
    A[i] = i;
  }

  float v = harlan_luminance(len, A,B);

  for (int i=0; i<len; i++) {
    for (int j=0; j<3; j++) {
      cout << B[i*3+j];
      cout << " ";
    }
    cout << endl;
  }
}
