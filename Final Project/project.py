from mininet.topo import Topo
from mininet.net import Mininet
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.cli import CLI
from mininet.node import Node
from mininet.link import TCLink
class MyTopo(Topo):

    def __init__(self):

        Topo.__init__(self)

        s1 = self.addSwitch('s1', dpid='0000000000000001')
        s2 = self.addSwitch('s2', dpid='0000000000000002')
        s3 = self.addSwitch('s3', dpid='0000000000000003')

        h1 = self.addHost('h1', mac='00:00:00:00:00:01', ip='10.0.2.1/16')
        h2 = self.addHost('h2', mac='00:00:00:00:00:02', ip='10.0.2.2/16')
        h3 = self.addHost('h3', mac='00:00:00:00:00:03', ip='10.0.3.1/16')
        h4 = self.addHost('h4', mac='00:00:00:00:00:04', ip='10.0.3.2/16')
        h5 = self.addHost('h5', mac='00:00:00:00:00:05', ip='0.0.0.0')

        self.addLink(s1, s2)
        self.addLink(s1, s3)

        self.addLink(s2, h1)
        self.addLink(s2, h2)
        self.addLink(s3, h3)
        self.addLink(s3, h4)
        self.addLink(s2, h5)
        


def run():
    topo = MyTopo()
    net = Mininet(topo=topo, controller=None, link=TCLink)
    net.addController('c0', controller=RemoteController, ip='127.0.0.1', port=6653)

    net.start()

    print("[+] Run DHCP server")
    dhcp = net.getNodeByName('h2')
    # dhcp.cmdPrint('service isc-dhcp-server restart &')
    dhcp.cmdPrint('/usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcpd.conf %s' % dhcp.defaultIntf())

    CLI(net)
    print("[-] Killing DHCP server")
    dhcp.cmdPrint("kill -9 `ps aux | grep h2-eth0 | grep dhcpd | awk '{print $2}'`")
    net.stop()

if __name__ == '__main__':
    setLogLevel('info')
    run()

